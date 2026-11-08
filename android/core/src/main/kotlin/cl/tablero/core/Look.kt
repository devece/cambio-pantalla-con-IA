package cl.tablero.core

/**
 * La apariencia que el usuario eligió en Ajustes: tema, distribución, fondo, hora del fondo, decoración y color de acento.
 * Un texto vacío significa "el de siempre" (lo que trae la pantalla por defecto): así quien nunca toca esto no cambia nada.
 * Los ids son los mismos del sitio (`public/shared/catalog.js`); `tests/look.test.js` comprueba que coincidan.
 *
 * Falta a propósito, por ahora: el diseño «Todoterreno» y los fondos de nieve, rocas y barro. El sitio los reserva para la
 * «camioneta 4x4», un tipo de auto que la app todavía no tiene; se agregan junto con él.
 */
data class Look(
    val theme: String = "",
    val layout: String = "",
    val scene: String = "",
    val sceneTime: String = "",
    val season: String = "",
    val accent: String = "",
) {
    /** Solo lo que está elegido y es válido; lo demás se deja a la pantalla por defecto. */
    fun cleaned(): Look = Look(
        theme = theme.takeIf { it in THEMES.keys } ?: "",
        layout = layout.takeIf { it in LAYOUTS.keys } ?: "",
        scene = scene.takeIf { it in SCENES.keys } ?: "",
        sceneTime = sceneTime.takeIf { it in SCENE_TIMES.keys } ?: "",
        season = season.takeIf { it in SEASONS.keys } ?: "",
        accent = accent.takeIf { it in ACCENTS } ?: "",
    )

    companion object {
        /** id -> nombre que ve el usuario. El orden es el de la lista en pantalla. */
        val THEMES: Map<String, String> = linkedMapOf(
            "sobrio" to "Sobrio", "ruta" to "Ruta", "papel" to "Papel", "cristal" to "Cristal", "alumbrado" to "Alumbrado",
            "cordillera" to "Cordillera", "costa" to "Costa", "atacama" to "Atacama", "patagonia" to "Patagonia",
        )
        val LAYOUTS: Map<String, String> = linkedMapOf(
            "consola" to "Consola", "estelar" to "Estelar", "tarjetas" to "Tarjetas", "centro" to "Auto al centro", "manejo" to "Modo manejo",
            "minimal" to "Minimalista", "tablero" to "Tablero", "lateral" to "Menú lateral",
        )
        val SCENES: Map<String, String> = linkedMapOf(
            "tema" to "Del diseño", "cordillera" to "Cordillera", "costa" to "Costa", "desierto" to "Desierto", "bosque" to "Bosque",
            "ciudad" to "Ciudad", "carretera" to "Carretera", "montana" to "Montaña", "color" to "Solo color",
        )
        val SCENE_TIMES: Map<String, String> = linkedMapOf(
            "auto" to "Automática (cambia con la hora)", "amanecer" to "Amanecer", "dia" to "Día", "atardecer" to "Atardecer", "noche" to "Noche",
        )
        val SEASONS: Map<String, String> = linkedMapOf(
            "ninguna" to "Sin decoración", "auto" to "Automática (según la fecha)", "patrias" to "Fiestas Patrias", "halloween" to "Halloween",
            "navidad" to "Navidad", "anonuevo" to "Año Nuevo",
        )
        val ACCENTS: List<String> = listOf("#ffd166", "#ff6b6b", "#4cc9f0", "#06d6a0", "#a78bfa", "#ff9f68", "#f472b6", "#ffffff")
    }
}
