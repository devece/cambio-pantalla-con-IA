package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleMapsTest {
    private val ok = mapOf("center" to "-33.45000,-70.66000", "zoom" to "15", "size" to "640x640", "scale" to "2", "maptype" to "roadmap")

    @Test
    fun `acepta la petición de la pantalla`() {
        val r = GoogleMaps.parse(ok)!!
        assertEquals(-33.45, r.lat, 1e-9)
        assertEquals(-70.66, r.lon, 1e-9)
        assertFalse(r.night)
        assertTrue(GoogleMaps.parse(ok + ("night" to "1"))!!.night)
    }

    @Test
    fun `rechaza todo lo que la pantalla no pide`() {
        listOf(
            ok - "center", ok + ("center" to "1,2,3"), ok + ("center" to "x,y"), ok + ("center" to "95,10"), ok + ("center" to "NaN,1"),
            ok + ("zoom" to "20"), ok + ("size" to "2048x2048"), ok + ("scale" to "4"), ok + ("maptype" to "satellite"), ok + ("night" to "si"),
        ).forEach { assertNull(GoogleMaps.parse(it), it.toString()) }
    }

    @Test
    fun `la dirección lleva la llave y el estilo de noche solo de noche`() {
        val dia = GoogleMaps.url(GoogleMaps.parse(ok)!!, "AIza-llave")
        assertTrue(dia.startsWith("https://maps.googleapis.com/maps/api/staticmap?center=-33.45000,-70.66000&zoom=15&size=640x640&scale=2"))
        assertTrue(dia.endsWith("&key=AIza-llave"))
        assertFalse(dia.contains("style="))
        val noche = GoogleMaps.url(GoogleMaps.parse(ok + ("night" to "1"))!!, "k")
        assertTrue(noche.contains("&style="))
        assertNotNull(Regex("style=element%3Ageometry%7Ccolor%3A0x1d2c4d").find(noche))
    }
}
