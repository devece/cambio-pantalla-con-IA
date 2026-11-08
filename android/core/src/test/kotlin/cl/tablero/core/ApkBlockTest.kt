package cl.tablero.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class ApkBlockTest {
    private fun le(size: Int, f: (ByteBuffer) -> Unit): ByteArray = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).also(f).array()

    /** Una «APK» mínima, igual que la de las pruebas del sitio: un archivo, un bloque de firmas con estos pares, directorio central y final. */
    private fun fakeApk(pairs: List<Pair<Int, ByteArray>>, withBlock: Boolean = true): ByteArray {
        val name = "a.txt".toByteArray()
        val data = "hola".toByteArray()
        val local = ByteArrayOutputStream().apply {
            write(le(30) { it.putInt(0x04034B50); it.putShort(10); it.putShort(0); it.putShort(0); it.putShort(0); it.putShort(0); it.putInt(0); it.putInt(data.size); it.putInt(data.size); it.putShort(name.size.toShort()); it.putShort(0) })
            write(name)
            write(data)
        }.toByteArray()
        val body = ByteArrayOutputStream().apply {
            pairs.forEach { (id, value) ->
                write(le(12) { it.putLong((4 + value.size).toLong()); it.putInt(id) })
                write(value)
            }
        }.toByteArray()
        val size = (body.size + 8 + 16).toLong()
        val block = if (!withBlock) ByteArray(0) else ByteArrayOutputStream().apply {
            write(le(8) { it.putLong(size) })
            write(body)
            write(le(8) { it.putLong(size) })
            write("APK Sig Block 42".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val cd = ByteArrayOutputStream().apply {
            write(le(46) { it.putInt(0x02014B50); it.putShort(20); it.putShort(10); it.putShort(0); it.putShort(0); it.putShort(0); it.putShort(0); it.putInt(0); it.putInt(data.size); it.putInt(data.size); it.putShort(name.size.toShort()); it.putShort(0); it.putShort(0); it.putShort(0); it.putShort(0); it.putInt(0); it.putInt(0) })
            write(name)
        }.toByteArray()
        val cdOffset = local.size + block.size
        val eocd = le(22) { it.putInt(0x06054B50); it.putShort(0); it.putShort(0); it.putShort(1); it.putShort(1); it.putInt(cd.size); it.putInt(cdOffset); it.putShort(0) }
        return local + block + cd + eocd
    }

    private fun temp(bytes: ByteArray): File = File.createTempFile("apk", ".apk").also { it.deleteOnExit(); it.writeBytes(bytes) }

    @Test
    fun `lee el diseno entre otros pares`() {
        val design = """{"v":1}""".toByteArray()
        val apk = temp(fakeApk(listOf(0x7109871A to "firma".toByteArray(), ApkBlock.DESIGN_ID to design, 0x42726577 to ByteArray(10))))
        assertContentEquals(design, ApkBlock.read(apk))
        assertContentEquals("firma".toByteArray(), ApkBlock.read(apk, 0x7109871A))
    }

    @Test
    fun `sin diseno devuelve null`() {
        assertNull(ApkBlock.read(temp(fakeApk(listOf(0x7109871A to "firma".toByteArray())))))
    }

    @Test
    fun `una APK sin bloque de firmas o un archivo cualquiera devuelven null`() {
        assertNull(ApkBlock.read(temp(fakeApk(emptyList(), withBlock = false))))
        assertNull(ApkBlock.read(temp(ByteArray(500) { 7 })))
        assertNull(ApkBlock.read(File("/no/existe.apk")))
    }
}
