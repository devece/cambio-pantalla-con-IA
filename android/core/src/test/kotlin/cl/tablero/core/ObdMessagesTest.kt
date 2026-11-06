package cl.tablero.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObdMessagesTest {
    @Test
    fun `cada error del adaptador tiene una explicacion util`() {
        for (e in ObdError.entries) {
            val text = ObdMessages.explain(e)
            assertTrue(text.length in 15..200, "$e: «$text»")
            assertTrue(text.trim().endsWith("."), "$e debe terminar en punto")
        }
    }

    @Test
    fun `las explicaciones dicen que hacer`() {
        assertTrue(ObdMessages.explain(ObdError.UNABLE_TO_CONNECT).contains("Enciende el auto"))
        assertTrue(ObdMessages.explain(ObdError.BUS_ERROR).contains("enchufado"))
        assertTrue(ObdMessages.explain(ObdError.NOT_ELM327).contains("ELM327"))
    }

    @Test
    fun `un error de conexion usa su propio mensaje`() {
        assertEquals("El adaptador no respondió a «010C»", ObdMessages.explain(IOException("El adaptador no respondió a «010C»")))
    }

    @Test
    fun `un error de conexion sin mensaje usa uno general`() {
        assertTrue(ObdMessages.explain(IOException()).contains("Revisa que esté bien enchufado"))
        assertTrue(ObdMessages.explain(IOException("  ")).contains("Revisa que esté bien enchufado"))
    }

    @Test
    fun `una excepcion de la sesion se explica por su tipo`() {
        assertEquals(ObdMessages.explain(ObdError.NO_DATA), ObdMessages.explain(ObdException(ObdError.NO_DATA, "x")))
    }

    @Test
    fun `cualquier otro error no deja el texto vacio`() {
        assertTrue(ObdMessages.explain(IllegalStateException("x")).isNotBlank())
    }
}
