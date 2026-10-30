package cl.tablero.core

/** Un comando de trazo SVG ya separado: `op` es M, L, Q o Z y `args` sus números. */
data class PathCmd(val op: Char, val args: List<Float>)

/**
 * Lector mínimo de trazos SVG. Solo entiende M, L, Q y Z con coordenadas absolutas,
 * que es todo lo que usan los autos de public/js/art.js.
 */
object PathData {
    private val ALLOWED = Regex("^[MLQZ0-9.,\\s-]+$")
    private val TOKEN = Regex("[MLQZ]|-?\\d*\\.?\\d+")
    private val ARITY = mapOf('M' to 2, 'L' to 2, 'Q' to 4, 'Z' to 0)

    fun parse(d: String): List<PathCmd> {
        require(ALLOWED.matches(d)) { "Trazo con comandos no soportados: $d" }
        val out = mutableListOf<PathCmd>()
        var op: Char? = null
        var nums = mutableListOf<Float>()
        for (m in TOKEN.findAll(d)) {
            val t = m.value
            if (t[0] in "MLQZ") {
                op = t[0]
                nums = mutableListOf()
                if (op == 'Z') out += PathCmd('Z', emptyList())
            } else {
                val current = requireNotNull(op) { "Número sin comando: $d" }
                nums += t.toFloat()
                if (nums.size == ARITY.getValue(current)) {
                    out += PathCmd(current, nums.toList())
                    nums = mutableListOf()
                    // Pares extra después de una M son líneas, como en SVG.
                    if (current == 'M') op = 'L'
                }
            }
        }
        return out
    }
}

/** Dibujo de un auto de perfil, en un lienzo de 400 x 150. */
data class CarShape(
    val body: String,
    val glass: List<String>,
    val wheels: List<Int>,
    /** x, y, ancho, alto y radio de un detalle oscuro (la tolva de la camioneta). */
    val extraRect: List<Float>? = null,
)

/** Mismas formas que `CARS` en public/js/art.js. */
object CarShapes {
    const val WIDTH = 400f
    const val HEIGHT = 150f

    val ALL: Map<String, CarShape> = linkedMapOf(
        "auto" to CarShape(
            body = "M14 106 L14 90 Q14 82 26 80 L74 74 L108 48 Q116 42 130 42 L238 42 Q252 42 262 52 L292 74 L358 82 Q386 86 386 102 L386 108 Q386 112 380 112 L14 112 Z",
            glass = listOf("M118 52 Q124 48 134 48 L198 48 L198 74 L96 74 Z", "M206 48 L238 48 Q246 48 252 55 L274 74 L206 74 Z"),
            wheels = listOf(102, 300),
        ),
        "suv" to CarShape(
            body = "M14 106 L14 86 Q14 78 26 76 L66 72 L100 42 Q106 36 120 36 L284 36 Q296 36 302 44 L330 74 L362 80 Q386 84 386 100 L386 108 Q386 112 380 112 L14 112 Z",
            glass = listOf("M112 46 Q116 42 124 42 L190 42 L190 72 L90 72 Z", "M198 42 L284 42 Q290 42 294 48 L314 72 L198 72 Z"),
            wheels = listOf(100, 304),
        ),
        "camioneta" to CarShape(
            body = "M14 108 L14 62 L150 62 L162 42 Q168 36 180 36 L262 36 Q272 36 278 44 L300 72 L360 78 Q386 82 386 100 L386 108 Q386 112 380 112 L14 112 Z",
            glass = listOf("M170 44 L226 44 L226 70 L156 70 Z", "M234 44 L262 44 Q268 44 272 50 L288 70 L234 70 Z"),
            wheels = listOf(96, 306),
            extraRect = listOf(22f, 66f, 122f, 6f, 2f),
        ),
    )

    fun of(type: String): CarShape = ALL[type] ?: ALL.getValue("auto")
}
