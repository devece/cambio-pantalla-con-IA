package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Primer arranque guiado: cuándo mostrarlo y qué le pasa la app a la pantalla de inicio con lo que el usuario contó
 * (nombre, auto y color). Sin Android, para poder probarlo en cualquier PC.
 */
object Welcome {
    const val NAME_MAX = 24

    /**
     * Se muestra una sola vez: cuando nunca se terminó ni se omitió, no hay un diseño bajado con un código
     * (quien ya activó eligió todo en el sitio) y tampoco puso su nombre en Ajustes (quien actualiza la app y ya la usa).
     */
    fun shouldShow(c: LauncherConfig, hasDesign: Boolean): Boolean = !c.welcomeDone && !hasDesign && c.name.isBlank()

    fun cleanName(raw: String): String = raw.trim().take(NAME_MAX)

    /**
     * Lo que el usuario contó, para la pantalla de inicio cuando aún no hay un diseño del sitio (edición gratuita).
     * Va en una clave aparte (`profile`) para no confundirse con un diseño real (el que se activó antes con un código).
     */
    /** Las opciones del cliente en el formato que lee la pantalla de la radio (public/shared/profile.js). */
    fun featuresJson(c: LauncherConfig): JSONObject = JSONObject()
        .put("country", c.country)
        .put("oilOn", c.oilOn)
        .put("odometer", c.odometer)
        .put("lastOil", c.lastOil)
        .put("oilEvery", c.oilEvery)
        .put("others", JSONArray(c.otherMaintenance))
        .put("docsOn", c.docsOn)
        .put("rtMonth", c.rtMonth)
        .put("fuelOn", c.fuelOn)
        .put("tankL", c.tankL)
        .put("kmPerL", c.kmPerL)
        .put("speedShow", c.speedShow)
        .put("speedAlert", c.speedAlert)
        .put("manualLimit", c.manualLimit)
        .put("restrOn", c.restrOn)
        .put("restrType", c.restrType)
        .put("restrDigit", c.restrDigit)
        .put("restrDays", JSONArray(c.restrDays))
        .put("voice", c.voice)
        .put("miles", c.miles)
        .put("gallons", c.gallons)
        .put("clock12", c.clock12)

    fun profileJson(c: LauncherConfig, photoUrl: String? = null, photoCut: Boolean = false, photoNightUrl: String? = null): String {
        val profile = JSONObject()
            .put("name", c.name)
            .put("greeting", c.greeting)
            .put("phrase", c.phrase)
            .put("type", c.carType)
            .put("color", c.carColor)
        if (c.clock12) profile.put("clock12", true)
        if (c.plate.isNotBlank()) profile.put("plate", c.plate)
        // Las opciones del cliente (aceite, documentos, combustible, restricción, país, unidades…): sin ellas la pantalla de la radio
        // se quedaba con los valores de fábrica. Las claves son las de public/shared/design-contract.json.
        profile.put("features", featuresJson(c))
        // La apariencia elegida en Ajustes: solo lo que el usuario escogió, para que lo demás quede como siempre.
        val look = c.look.cleaned()
        val style = JSONObject()
        if (look.theme.isNotEmpty()) style.put("theme", look.theme)
        if (look.layout.isNotEmpty()) style.put("layout", look.layout)
        if (look.scene.isNotEmpty()) style.put("scene", look.scene)
        if (look.sceneTime.isNotEmpty()) style.put("sceneTime", look.sceneTime)
        if (look.season.isNotEmpty()) style.put("season", look.season)
        if (look.accent.isNotEmpty()) style.put("accent", look.accent)
        if (style.length() > 0) profile.put("style", style)
        val out = JSONObject().put("profile", profile)
        // La foto del auto que eligió en Ajustes (la pantalla de inicio la muestra igual que la de un diseño del sitio).
        if (!photoUrl.isNullOrEmpty()) {
            val photo = JSONObject().put("day", photoUrl).put("cut", photoCut)
            if (photoCut && !photoNightUrl.isNullOrEmpty()) photo.put("night", photoNightUrl)
            out.put("photo", photo)
        }
        return out.toString()
    }
}
