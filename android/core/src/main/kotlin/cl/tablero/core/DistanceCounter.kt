package cl.tablero.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Un punto del GPS, sin depender de Android (así se puede probar en cualquier PC). */
data class GpsFix(
    val lat: Double,
    val lon: Double,
    /** Hora del punto en milisegundos (la que informa el GPS). */
    val timeMs: Long,
    /** Error estimado en metros, o null si la radio no lo informa. */
    val accuracyM: Float? = null,
    /** Velocidad en metros por segundo según el GPS, o null si no la informa. */
    val speedMps: Float? = null,
)

/**
 * Suma los kilómetros recorridos a partir de los puntos del GPS, para que el kilometraje del auto
 * (y con él los avisos de aceite y mantención) avance solo.
 *
 * Es una **estimación**: el GPS no ve bien en túneles y estacionamientos subterráneos, así que el
 * kilometraje del tablero siempre manda. El usuario puede corregir el número en Ajustes.
 *
 * Reglas, para no contar kilómetros que no se manejaron:
 * - un punto con mucho error ([maxAccuracyM]) se descarta;
 * - la "deriva" del GPS con el auto detenido no suma: si el GPS informa velocidad, hay que ir a más de
 *   [minMovingKmh]; si no la informa, un desplazamiento pequeño (menor que [NOISE_FACTOR] veces el error
 *   del punto, y nunca menos de [MIN_NOISE_M]) se toma como ruido y el punto de partida no se mueve;
 * - un salto imposible (más de [maxKmh] entre dos puntos) no suma;
 * - si pasó mucho tiempo entre dos puntos ([maxGapMs]: el auto estuvo apagado o sin señal) no se
 *   cuenta la línea recta entre ellos, solo se toma el nuevo punto como partida.
 *
 * @param carryMeters metros ya acumulados que todavía no completan un kilómetro (para no perderlos al reiniciar).
 */
class DistanceCounter(
    carryMeters: Int = 0,
    private val maxAccuracyM: Float = 25f,
    private val minMovingKmh: Float = 3f,
    private val maxKmh: Float = 200f,
    private val maxGapMs: Long = 10 * 60_000L,
) {
    private var last: GpsFix? = null
    private var carry: Double = carryMeters.coerceIn(0, 999).toDouble()

    /** Metros acumulados que aún no suman un kilómetro (0..999). Se guarda para no perderlos. */
    val carryMeters: Int get() = carry.toInt()

    /** Olvida el último punto (por ejemplo al apagar el GPS) sin perder los metros acumulados. */
    fun resetTrack() {
        last = null
    }

    /** Descarta también los metros acumulados (cuando el usuario corrige el kilometraje a mano). */
    fun reset() {
        last = null
        carry = 0.0
    }

    /** Procesa un punto del GPS y devuelve los **kilómetros enteros** que se completaron con él (casi siempre 0). */
    fun onFix(fix: GpsFix): Int {
        if (fix.accuracyM != null && fix.accuracyM > maxAccuracyM) return 0
        val prev = last
        if (prev == null) {
            last = fix
            return 0
        }
        val dt = fix.timeMs - prev.timeMs
        if (dt <= 0) return 0 // punto repetido o fuera de orden
        if (dt > maxGapMs) {
            last = fix
            return 0
        }

        val meters = distanceMeters(prev.lat, prev.lon, fix.lat, fix.lon)
        val reportedKmh = fix.speedMps?.let { it * 3.6 }
        if (reportedKmh != null) {
            // La velocidad del GPS (efecto Doppler) es fiable: con el auto detenido marca casi cero aunque el punto "baile".
            last = fix
            if (reportedKmh < minMovingKmh) return 0
        } else {
            // Sin velocidad del GPS: moverse menos que el ruido normal no cuenta, y se sigue midiendo desde el mismo punto.
            if (meters < maxOf(NOISE_FACTOR * (fix.accuracyM ?: 0f), MIN_NOISE_M)) return 0
            last = fix
        }
        if (meters / (dt / 1000.0) * 3.6 > maxKmh) return 0 // salto imposible

        carry += meters
        val km = (carry / 1000.0).toInt()
        carry -= km * 1000.0
        return km
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Sin velocidad del GPS: lo que se mueva el punto por debajo de esto (en metros) es ruido. */
        const val MIN_NOISE_M = 15f
        const val NOISE_FACTOR = 2f

        /** Distancia en metros entre dos puntos (fórmula de haversine). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
        }
    }
}
