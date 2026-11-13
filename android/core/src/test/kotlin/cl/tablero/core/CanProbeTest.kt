package cl.tablero.core

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanProbeTest {
    private fun le16(o: ByteArrayOutputStream, v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
    private fun le32(o: ByteArrayOutputStream, v: Int) { le16(o, v and 0xFFFF); le16(o, (v shr 16) and 0xFFFF) }

    /** Un AndroidManifest.xml binario mínimo: solo el encabezado y la tabla de textos. */
    private fun manifest(strings: List<String>, utf8: Boolean): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        for (s in strings) {
            offsets.add(data.size())
            if (utf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                data.write(s.length); data.write(bytes.size); data.write(bytes); data.write(0)
            } else {
                le16(data, s.length); data.write(s.toByteArray(Charsets.UTF_16LE)); le16(data, 0)
            }
        }
        while (data.size() % 4 != 0) data.write(0)
        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4
        val chunkSize = stringsStart + data.size()
        val out = ByteArrayOutputStream()
        le16(out, 0x0003); le16(out, 8); le32(out, 8 + chunkSize) // documento
        le16(out, 0x0001); le16(out, headerSize); le32(out, chunkSize) // tabla de textos
        le32(out, strings.size); le32(out, 0); le32(out, if (utf8) 0x100 else 0); le32(out, stringsStart); le32(out, 0)
        offsets.forEach { le32(out, it) }
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private val strings = listOf(
        "manifest", "com.raise.canbus.ACTION_CAR_DATA", "android.permission.INTERNET", "android.intent.category.DEFAULT",
        "com.raise.canbus.MainActivity", "hola mundo.action", "com.syu.broadcast.update",
    )

    @Test
    fun `saca las acciones de un manifiesto en UTF-16`() {
        assertEquals(listOf("com.raise.canbus.ACTION_CAR_DATA", "com.syu.broadcast.update"), CanProbe.manifestActions(manifest(strings, utf8 = false)))
    }

    @Test
    fun `saca las acciones de un manifiesto en UTF-8`() {
        assertEquals(listOf("com.raise.canbus.ACTION_CAR_DATA", "com.syu.broadcast.update"), CanProbe.manifestActions(manifest(strings, utf8 = true)))
    }

    @Test
    fun `un archivo que no es un manifiesto da lista vacía y no falla`() {
        assertTrue(CanProbe.manifestActions(ByteArray(0)).isEmpty())
        assertTrue(CanProbe.manifestActions(ByteArray(200) { 7 }).isEmpty())
    }

    @Test
    fun `reconoce apps de CanBus por paquete o nombre`() {
        assertTrue(CanProbe.isCandidate("com.hiworld.canbus", "CarSet"))
        assertTrue(CanProbe.isCandidate("com.foo.bar", "Raise CanBus"))
        assertTrue(!CanProbe.isCandidate("com.spotify.music", "Spotify"))
    }

    @Test
    fun `reconoce nombres de puertos serie`() {
        assertTrue(CanProbe.isSerialName("ttyS3"))
        assertTrue(CanProbe.isSerialName("ttyMT1"))
        assertTrue(!CanProbe.isSerialName("tty"))
        assertTrue(!CanProbe.isSerialName("null"))
    }

    @Test
    fun `el reporte muestra lo hallado y lo que llegó`() {
        val r = CanProbe.report(
            CanProbeInfo(
                candidates = listOf(CanCandidate("com.raise.canbus", "CanBus", true, listOf("com.raise.canbus.CarService"), emptyList(), listOf("com.raise.provider"), listOf("com.raise.canbus.ACTION_CAR_DATA"))),
                serialPorts = listOf(SerialPortInfo("/dev/ttyS1", canRead = false, canWrite = false), SerialPortInfo("/dev/ttyS3", canRead = true, canWrite = true)),
                captured = listOf(CapturedBroadcast("com.raise.canbus.ACTION_CAR_DATA", 5, listOf("rpm=1800"))),
                listening = 1,
            ),
        )
        listOf("CanBus (com.raise.canbus)", "servicios abiertos: CarService", "com.raise.provider", "accesibles para Tablero: 1", "/dev/ttyS3", "×5", "rpm=1800").forEach {
            assertTrue(it in r, "falta «$it»")
        }
    }

    @Test
    fun `sin hallazgos el reporte dice qué hacer`() {
        val r = CanProbe.report(CanProbeInfo(emptyList(), emptyList(), emptyList(), 0))
        assertTrue("No hay ninguna con nombre conocido" in r)
        assertTrue("No ha llegado nada" in r)
    }
}
