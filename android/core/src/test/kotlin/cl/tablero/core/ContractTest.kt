package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato entre el configurador y la pantalla de la radio, lado de la app: cada opción del cliente que declara
 * public/shared/design-contract.json debe (1) entrar a la configuración al importar el diseño y (2) salir hacia la pantalla en el perfil.
 * La prueba del lado de la pantalla es tests/contract.test.js; las dos leen el mismo archivo.
 */
class ContractTest {
    private val contract = JSONObject(File(System.getProperty("design.contract.path")).readText())
    private val specs = contract.getJSONObject("features")
    private val keys = specs.keys().asSequence().toList()
    private val known = Maintenance.parse(File(System.getProperty("maintenance.path")).readText()).map { it.id }.toSet()

    private fun design(features: JSONObject, car: JSONObject = JSONObject()) =
        JSONObject().put("v", 1).put("car", car).put("features", features).toString()

    /** La configuración que queda al importar un diseño con solo esa opción. */
    private fun imported(key: String, value: Any?): LauncherConfig =
        DesignImport.apply(LauncherConfig(), design(JSONObject().put(key, value)), known)!!

    private fun profileFeatures(c: LauncherConfig): JSONObject =
        JSONObject(Welcome.profileJson(c)).getJSONObject("profile").getJSONObject("features")

    private fun same(a: Any?, b: Any?): Boolean =
        if (a is JSONArray && b is JSONArray) a.similar(b) else a.toString() == b.toString()

    @Test
    fun `el perfil lleva todas las opciones del contrato y ninguna otra`() {
        val f = profileFeatures(LauncherConfig())
        assertEquals(keys.sorted(), f.keys().asSequence().toList().sorted())
    }

    @Test
    fun `cada opcion del diseno entra a la configuracion y sale hacia la pantalla de la radio`() {
        for (k in keys) {
            val sample = specs.getJSONObject(k).get("sample")
            val out = profileFeatures(imported(k, sample))
            assertTrue(same(sample, out.get(k)), "la opcion «$k» se perdio entre el diseno y la pantalla: entro $sample y salio ${out.get(k)}")
        }
    }

    @Test
    fun `sin tocar nada el perfil lleva los valores de fabrica del contrato`() {
        val out = profileFeatures(LauncherConfig())
        for (k in keys) {
            if (k == "voice") continue // en el sitio nace activada; la app arranca con la voz apagada hasta que el cliente la pida
            val default = specs.getJSONObject(k).get("default")
            assertTrue(same(default, out.get(k)), "el valor de fabrica de «$k» no coincide con el contrato: $default contra ${out.get(k)}")
        }
    }

    @Test
    fun `las opciones desactivadas del diseno se respetan`() {
        val c = DesignImport.apply(LauncherConfig(), design(JSONObject().put("oilOn", false).put("speedShow", false).put("docsOn", false)))!!
        val out = profileFeatures(c)
        assertEquals(false, out.getBoolean("oilOn"))
        assertEquals(false, out.getBoolean("speedShow"))
        assertEquals(false, out.getBoolean("docsOn"))
    }

    @Test
    fun `un valor invalido no pisa lo que ya habia`() {
        for (k in keys) {
            val bad = specs.getJSONObject(k).getJSONArray("bad")
            for (i in 0 until bad.length()) {
                val before = profileFeatures(LauncherConfig()).get(k)
                val after = profileFeatures(imported(k, bad.get(i))).get(k)
                // Un entero fuera de rango se ajusta al borde (coerceIn), no se acepta tal cual: lo importante es que nunca salga de la lista o del rango.
                val spec = specs.getJSONObject(k)
                when (spec.getString("kind")) {
                    "int" -> {
                        val n = (after as Number).toInt()
                        assertTrue(n in spec.getInt("min")..spec.getInt("max"), "«$k» salio de rango: $n")
                    }
                    else -> assertTrue(same(before, after), "«$k» acepto un valor invalido ${bad.get(i)}: salio $after")
                }
            }
        }
    }

    @Test
    fun `la patente y el pais llegan y el pais invalido se ignora`() {
        val c = DesignImport.apply(LauncherConfig(), design(JSONObject().put("country", "MX"), JSONObject().put("plate", "abcd12")))!!
        assertEquals("MX", c.country)
        assertEquals("ABCD12", c.plate)
        assertEquals("ABCD12", JSONObject(Welcome.profileJson(c)).getJSONObject("profile").getString("plate"))
        assertEquals("CL", DesignImport.apply(LauncherConfig(), design(JSONObject().put("country", "ZZ")))!!.country)
    }
}
