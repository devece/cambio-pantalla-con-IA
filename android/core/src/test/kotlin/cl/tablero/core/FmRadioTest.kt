package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FmRadioTest {
    @Test
    fun `lee la frecuencia en varios formatos`() {
        assertEquals(96.1, FmRadio.frequencyIn("96.1"))
        assertEquals(96.1, FmRadio.frequencyIn("96,1"))
        assertEquals(96.1, FmRadio.frequencyIn("FM 96.1 MHz"))
        assertEquals(101.7, FmRadio.frequencyIn("FM1 101.7"))
        assertEquals(88.0, FmRadio.frequencyIn("88"))
        assertEquals(96.1, FmRadio.frequencyIn("96100"), "en kHz")
        assertEquals(93.35, FmRadio.frequencyIn("93.35"))
    }

    @Test
    fun `descarta lo que no es una frecuencia FM`() {
        assertNull(FmRadio.frequencyIn(null))
        assertNull(FmRadio.frequencyIn(""))
        assertNull(FmRadio.frequencyIn("AM 1010"), "AM va en kHz y pasa de 108")
        assertNull(FmRadio.frequencyIn("Radio Cooperativa"))
        assertNull(FmRadio.frequencyIn("87.4"), "bajo la banda")
        assertNull(FmRadio.frequencyIn("108.1"), "sobre la banda")
        assertNull(FmRadio.frequencyIn("Preset 3"))
    }

    @Test
    fun `no confunde una frecuencia con parte de otro numero`() {
        assertNull(FmRadio.frequencyIn("1996.1"))
        assertNull(FmRadio.frequencyIn("196.1"))
    }

    @Test
    fun `toma la frecuencia del titulo y el nombre del artista`() {
        val s = FmRadio.parse("FM 96.1 MHz", "Radio Futuro")
        assertEquals(96.1, s.frequency)
        assertEquals("Radio Futuro", s.name)
    }

    @Test
    fun `el nombre puede venir junto a la frecuencia en el titulo`() {
        val s = FmRadio.parse("Radio Cooperativa 93.3", null)
        assertEquals(93.3, s.frequency)
        assertEquals("Radio Cooperativa", s.name)
    }

    @Test
    fun `la frecuencia puede venir en el artista`() {
        val s = FmRadio.parse("Pudahuel", "FM 90.5")
        assertEquals(90.5, s.frequency)
        assertEquals("Pudahuel", s.name)
    }

    @Test
    fun `solo frecuencia deja el nombre vacio y sin datos no inventa nada`() {
        assertEquals(FmStation(99.3, ""), FmRadio.parse("99.3 MHz", null))
        assertEquals(FmStation(99.3, ""), FmRadio.parse("FM 99.3", ""))
        assertEquals(FmStation(null, ""), FmRadio.parse(null, null))
        assertEquals(FmStation(null, "Sin señal"), FmRadio.parse("Sin señal", null))
    }

    @Test
    fun `reconoce las apps de radio FM`() {
        listOf("com.android.fmradio", "com.nwd.radio", "com.acme.fmradio.pro", "com.vendor.radio").forEach { assertTrue(FmRadio.isRadioPackage(it), it) }
        listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.waze", "com.radioapp.player").forEach { assertFalse(FmRadio.isRadioPackage(it), it) }
    }
}
