package cl.tablero.core

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
     * Va en una clave aparte (`profile`) para no confundirse con un diseño real, que además oculta el botón "Activar código".
     */
    fun profileJson(c: LauncherConfig, photoUrl: String? = null, photoCut: Boolean = false): String {
        val profile = JSONObject()
            .put("name", c.name)
            .put("greeting", c.greeting)
            .put("phrase", c.phrase)
            .put("type", c.carType)
            .put("color", c.carColor)
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
        if (!photoUrl.isNullOrEmpty()) out.put("photo", JSONObject().put("day", photoUrl).put("cut", photoCut))
        return out.toString()
    }
}
