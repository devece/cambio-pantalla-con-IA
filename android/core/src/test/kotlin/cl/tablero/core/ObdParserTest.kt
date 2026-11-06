package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObdParserTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    private fun errorOf(raw: String, command: String = "0100"): ObdError =
        assertFailsWith<ObdException> { ObdParser.messages(raw, command) }.error

    @Test
    fun `una respuesta simple con o sin espacios da los mismos bytes`() {
        assertEquals(listOf("410C1AF8"), ObdParser.messages("41 0C 1A F8 \r\r", "010C").map(::hex))
        assertEquals(listOf("410C1AF8"), ObdParser.messages("410C1AF8\r", "010C").map(::hex))
        assertEquals(listOf("410C1AF8"), ObdParser.messages("410c1af8", "010C").map(::hex), "las minúsculas también")
    }

    @Test
    fun `el eco del comando y SEARCHING no estorban`() {
        val raw = "010C\rSEARCHING...\r41 0C 1A F8 \r\r"
        assertEquals(listOf("410C1AF8"), ObdParser.messages(raw, "010C").map(::hex))
    }

    @Test
    fun `el aviso mayor que que deja el adaptador no estorba`() {
        assertEquals(listOf("410D3C"), ObdParser.messages("410D3C\r\r>", "010D").map(::hex))
    }

    @Test
    fun `varias ECUs que contestan dan varios mensajes`() {
        val raw = "41 0D 3C \r41 0D 3D \r"
        assertEquals(listOf("410D3C", "410D3D"), ObdParser.messages(raw, "010D").map(::hex))
    }

    @Test
    fun `una respuesta larga en varias lineas se une en un solo mensaje, con o sin aviso del largo`() {
        val conLargo = "014\r0: 49 02 01 31 47 31\r1: 4A 43 35 34 34 34 52\r2: 37 32 35 32 33 36 37\r"
        val sinEspacios = "014\r0:490201314731\r1:4A433534343452\r2:37323532333637"
        val esperado = "490201314731" + "4A433534343452" + "37323532333637"
        assertEquals(listOf(esperado), ObdParser.messages(conLargo, "0902").map(::hex))
        assertEquals(listOf(esperado), ObdParser.messages(sinEspacios, "0902").map(::hex))
        // Sin el aviso del largo (algunos adaptadores no lo mandan) tambien funciona.
        assertEquals(listOf(esperado), ObdParser.messages(conLargo.substringAfter('\r'), "0902").map(::hex))
    }

    @Test
    fun `dos respuestas largas seguidas (dos ECUs) no se mezclan`() {
        val raw = "0: 49 02 01 41 42 43\r1: 44 45 46 47\r0: 49 02 01 51 52 53\r1: 54 55 56 57\r"
        assertEquals(listOf("490201414243" + "44454647", "490201515253" + "54555657"), ObdParser.messages(raw, "0902").map(::hex))
    }

    @Test
    fun `los errores del adaptador salen como errores claros`() {
        assertEquals(ObdError.NO_DATA, errorOf("NO DATA\r\r"))
        assertEquals(ObdError.NO_DATA, errorOf("SEARCHING...\rNO DATA\r"))
        assertEquals(ObdError.UNABLE_TO_CONNECT, errorOf("SEARCHING...\rUNABLE TO CONNECT\r"))
        assertEquals(ObdError.UNKNOWN_COMMAND, errorOf("?\r"))
        assertEquals(ObdError.BUS_ERROR, errorOf("BUS INIT: ...ERROR\r"))
        assertEquals(ObdError.BUS_ERROR, errorOf("CAN ERROR\r"))
        assertEquals(ObdError.BUS_ERROR, errorOf("<DATA ERROR\r"))
        assertEquals(ObdError.BUS_ERROR, errorOf("ERR94\r"))
        assertEquals(ObdError.ADAPTER_BUSY, errorOf("BUS BUSY\r"))
        assertEquals(ObdError.ADAPTER_BUSY, errorOf("STOPPED\r"))
        assertEquals(ObdError.ADAPTER_BUSY, errorOf("BUFFER FULL\r"))
        assertEquals(ObdError.ADAPTER_BUSY, errorOf("LV RESET\r"))
    }

    @Test
    fun `BUS INIT OK es solo un aviso y no un error`() {
        val raw = "BUS INIT: OK\r41 0C 1A F8 \r"
        assertEquals(listOf("410C1AF8"), ObdParser.messages(raw, "010C").map(::hex))
    }

    @Test
    fun `una respuesta sin datos es una respuesta mala`() {
        assertEquals(ObdError.BAD_RESPONSE, errorOf(""))
        assertEquals(ObdError.BAD_RESPONSE, errorOf("OK\r"))
        assertEquals(ObdError.BAD_RESPONSE, errorOf("ZZ12\r"))
    }

    @Test
    fun `la basura entre medio se ignora si hay datos`() {
        assertEquals(listOf("410D3C"), ObdParser.messages("OK\r410D3C\r", "010D").map(::hex))
    }

    @Test
    fun `lines entrega las lineas normalizadas`() {
        assertEquals(listOf("ELM327V1.5"), ObdParser.lines("\r\rELM327 v1.5\r\r>", "ATZ"))
        assertEquals(listOf("OK"), ObdParser.lines("ATE0\rOK\r\r", "ATE0"))
        assertEquals(emptyList(), ObdParser.lines("", "ATZ"))
    }

    @Test
    fun `toBytes valida el hexadecimal`() {
        assertContentEquals(byteArrayOf(0x41, 0x0C), ObdParser.toBytes("410C"))
        assertEquals(null, ObdParser.toBytes("410"))
        assertEquals(null, ObdParser.toBytes("41GG"))
        assertEquals(null, ObdParser.toBytes(""))
    }
}
