package cl.tablero.core

/** Consumo de combustible a partir de lo que entrega el auto. Lógica pura, sin Android. */
object ObdFuel {
    /** Relación aire/combustible ideal de la bencina (kg de aire por kg de bencina). */
    private const val GASOLINE_AIR_FUEL = 14.7

    /** Densidad de la bencina, en gramos por litro. */
    private const val GASOLINE_G_PER_L = 740.0

    /** Bajo esta velocidad (km/h) no se calcula litros cada 100 km: con el auto casi detenido el número no significa nada. */
    const val MIN_SPEED_KMH = 5.0

    /**
     * Litros por hora que gasta el motor. Usa el dato directo del auto (`fuelrate`) si lo entrega; si no, lo estima desde el
     * flujo de aire (`maf`) suponiendo un motor a bencina. Null si no hay de dónde sacarlo.
     */
    fun litersPerHour(values: Map<String, Double>): Double? {
        values["fuelrate"]?.let { return it }
        val maf = values["maf"] ?: return null
        return maf * 3600.0 / (GASOLINE_AIR_FUEL * GASOLINE_G_PER_L)
    }

    /** Litros cada 100 km al instante. Null si el auto va casi detenido o falta algún dato. */
    fun litersPer100Km(litersPerHour: Double?, speedKmh: Double?): Double? {
        if (litersPerHour == null || speedKmh == null || speedKmh < MIN_SPEED_KMH) return null
        return litersPerHour / speedKmh * 100.0
    }

    /** Agrega al mapa de valores el gasto (`flow`, L/h) y el consumo (`l100`, L/100 km) cuando se pueden calcular. */
    fun addDerived(values: MutableMap<String, Double>) {
        val flow = litersPerHour(values)
        if (flow != null) values["flow"] = flow else values.remove("flow")
        val l100 = litersPer100Km(flow, values["speed"])
        if (l100 != null) values["l100"] = l100 else values.remove("l100")
    }
}
