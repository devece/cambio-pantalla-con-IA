package cl.tablero.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lee un dato propio del «bloque de firmas» de una APK. El sitio le agrega el diseño del cliente a la APK que descarga
 * (ver `lib/apk-inject.js`), y la app lo lee de su propio archivo al abrirse. Si no hay diseño o algo no cuadra, devuelve null:
 * la app sigue como siempre.
 *
 * Diseño del archivo:  [entradas del zip] [bloque de firmas] [directorio central] [fin del zip]
 * Bloque de firmas:    [tamaño u64] [pares ...] [tamaño u64] ["APK Sig Block 42"]
 * Cada par:            [largo u64 = 4 + valor] [id u32] [valor]
 */
object ApkBlock {
    /** Identificadores de Tablero («TBL1» diseño, «TBL2» foto de día, «TBL3» foto de noche); los mismos de `lib/apk-inject.js`. */
    const val DESIGN_ID = 0x54424C31
    const val PHOTO_DAY_ID = 0x54424C32
    const val PHOTO_NIGHT_ID = 0x54424C33

    private const val MAX_BLOCK = 16L * 1024 * 1024
    private const val EOCD_SIGNATURE = 0x06054B50L
    private val MAGIC = "APK Sig Block 42".toByteArray(Charsets.ISO_8859_1)

    /** El valor guardado con ese id en la APK `file`, o null. */
    fun read(file: File, id: Int = DESIGN_ID): ByteArray? = try {
        RandomAccessFile(file, "r").use { read(it, id) }
    } catch (e: Exception) {
        null
    }

    private fun read(raf: RandomAccessFile, id: Int): ByteArray? {
        val length = raf.length()
        if (length < 64) return null
        val tailLen = minOf(length, 22L + 0xFFFF).toInt()
        val tail = ByteArray(tailLen)
        raf.seek(length - tailLen)
        raf.readFully(tail)
        var eocd = -1
        var i = tailLen - 22
        while (i >= 0) {
            if (u32(tail, i) == EOCD_SIGNATURE && i + 22 + u16(tail, i + 20) == tailLen) {
                eocd = i
                break
            }
            i--
        }
        if (eocd < 0) return null
        val cdOffset = u32(tail, eocd + 16)
        if (cdOffset < 32 || cdOffset > length) return null

        val magic = ByteArray(16)
        raf.seek(cdOffset - 16)
        raf.readFully(magic)
        if (!magic.contentEquals(MAGIC)) return null

        val sizeBytes = ByteArray(8)
        raf.seek(cdOffset - 24)
        raf.readFully(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).long
        val blockStart = cdOffset - size - 8
        if (size < 24 || size > MAX_BLOCK || blockStart < 0) return null

        // size = pares + el segundo tamaño (8) + la magia (16)
        val pairs = ByteArray((size - 24).toInt())
        raf.seek(blockStart + 8)
        raf.readFully(pairs)
        val buf = ByteBuffer.wrap(pairs).order(ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 12) {
            val len = buf.long
            if (len < 4 || len > buf.remaining()) return null
            val pairId = buf.int
            val valueLen = (len - 4).toInt()
            if (pairId == id) {
                val value = ByteArray(valueLen)
                buf.get(value)
                return value
            }
            buf.position(buf.position() + valueLen)
        }
        return null
    }

    private fun u16(b: ByteArray, offset: Int): Int = ByteBuffer.wrap(b, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun u32(b: ByteArray, offset: Int): Long = ByteBuffer.wrap(b, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
}
