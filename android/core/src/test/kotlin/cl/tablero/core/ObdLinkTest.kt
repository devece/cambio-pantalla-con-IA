package cl.tablero.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdLinkTest {
    @Test
    fun `los tipos se reconocen por su id y lo desconocido es sin adaptador`() {
        assertEquals(ObdLinkKind.BLUETOOTH, ObdLinkKind.of("bluetooth"))
        assertEquals(ObdLinkKind.WIFI, ObdLinkKind.of("wifi"))
        assertEquals(ObdLinkKind.OFF, ObdLinkKind.of("off"))
        assertEquals(ObdLinkKind.OFF, ObdLinkKind.of(null))
        assertEquals(ObdLinkKind.OFF, ObdLinkKind.of("usb"))
        assertEquals(3, ObdLinkKind.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `las direcciones Bluetooth se validan`() {
        assertTrue(ObdLinkConfig.isValidMac("AA:BB:CC:DD:EE:FF"))
        assertTrue(ObdLinkConfig.isValidMac(" 00:1d:a5:68:98:8b "))
        assertFalse(ObdLinkConfig.isValidMac("AA:BB:CC:DD:EE"))
        assertFalse(ObdLinkConfig.isValidMac("AA-BB-CC-DD-EE-FF"))
        assertFalse(ObdLinkConfig.isValidMac("GG:BB:CC:DD:EE:FF"))
        assertFalse(ObdLinkConfig.isValidMac(""))
    }

    @Test
    fun `los nombres de equipo y las IP se validan`() {
        assertTrue(ObdLinkConfig.isValidHost("192.168.0.10"))
        assertTrue(ObdLinkConfig.isValidHost("elm327.local"))
        assertTrue(ObdLinkConfig.isValidHost("a"))
        assertFalse(ObdLinkConfig.isValidHost(""))
        assertFalse(ObdLinkConfig.isValidHost("192 168 0 10"))
        assertFalse(ObdLinkConfig.isValidHost("host con espacios"))
        assertFalse(ObdLinkConfig.isValidHost("-malo"))
        assertFalse(ObdLinkConfig.isValidHost("http://x"))
    }

    @Test
    fun `un adaptador Bluetooth esta configurado solo con una direccion valida`() {
        assertTrue(ObdLinkConfig(ObdLinkKind.BLUETOOTH, "AA:BB:CC:DD:EE:FF", "OBDII").isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.BLUETOOTH, "").isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.BLUETOOTH, "no es una mac").isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.OFF, "AA:BB:CC:DD:EE:FF").isConfigured)
    }

    @Test
    fun `un adaptador Wi-Fi necesita host y puerto validos`() {
        assertTrue(ObdLinkConfig(ObdLinkKind.WIFI, "192.168.0.10", port = 35000).isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.WIFI, "192.168.0.10", port = 0).isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.WIFI, "192.168.0.10", port = 70000).isConfigured)
        assertFalse(ObdLinkConfig(ObdLinkKind.WIFI, "").isConfigured)
    }

    @Test
    fun `lo guardado se limpia y nunca rompe`() {
        val bt = ObdLinkConfig.fromStored("bluetooth", " aa:bb:cc:dd:ee:ff ", "  Mi OBD  ", 35000)
        assertEquals(ObdLinkKind.BLUETOOTH, bt.kind)
        assertEquals("AA:BB:CC:DD:EE:FF", bt.address)
        assertEquals("Mi OBD", bt.name)

        // Datos dañados: se vuelve a "sin adaptador" en vez de dejar algo a medias.
        assertEquals(ObdLinkKind.OFF, ObdLinkConfig.fromStored("bluetooth", "basura", "x", 35000).kind)
        assertEquals(ObdLinkKind.OFF, ObdLinkConfig.fromStored("wifi", "", "", 35000).kind)
        assertEquals(ObdLinkKind.OFF, ObdLinkConfig.fromStored(null, null, null, 0).kind)
        assertEquals(ObdLinkKind.OFF, ObdLinkConfig.fromStored("algo raro", "AA:BB:CC:DD:EE:FF", "x", 1).kind)

        // Un puerto raro vuelve al de siempre.
        assertEquals(35000, ObdLinkConfig.fromStored("wifi", "192.168.0.10", "", 0).port)
        assertEquals(35000, ObdLinkConfig.fromStored("wifi", "192.168.0.10", "", 99999).port)
        assertEquals(6789, ObdLinkConfig.fromStored("wifi", "192.168.0.10", "", 6789).port)

        // Un nombre larguísimo se recorta.
        assertEquals(40, ObdLinkConfig.fromStored("bluetooth", "AA:BB:CC:DD:EE:FF", "x".repeat(200), 35000).name.length)
    }

    @Test
    fun `como se muestra cada adaptador`() {
        assertEquals("OBDII", ObdLinkConfig(ObdLinkKind.BLUETOOTH, "AA:BB:CC:DD:EE:FF", "OBDII").display)
        assertEquals("AA:BB:CC:DD:EE:FF", ObdLinkConfig(ObdLinkKind.BLUETOOTH, "AA:BB:CC:DD:EE:FF", "").display)
        assertEquals("192.168.0.10:35000", ObdLinkConfig(ObdLinkKind.WIFI, "192.168.0.10").display)
        assertEquals("Sin adaptador", ObdLinkConfig().display)
    }

    @Test
    fun `los valores de fabrica de los adaptadores Wi-Fi`() {
        assertEquals("192.168.0.10", ObdLinkConfig.DEFAULT_WIFI_HOST)
        assertEquals(35000, ObdLinkConfig.DEFAULT_WIFI_PORT)
        assertEquals(ObdLinkKind.OFF, LauncherConfig().obd.kind)
    }

    @Test
    fun `un conector se puede escribir como una funcion`() {
        val channel = FakeElm()
        val connector = ObdConnector { channel }
        assertTrue(connector.open() === channel)
        val failing = ObdConnector { throw IOException("sin Bluetooth") }
        assertEquals("sin Bluetooth", kotlin.runCatching { failing.open() }.exceptionOrNull()?.message)
    }
}
