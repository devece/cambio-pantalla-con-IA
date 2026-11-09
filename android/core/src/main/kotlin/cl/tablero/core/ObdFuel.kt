package cl.tablero.core

/** Consumo de combustible a partir de lo que entrega el auto. Lógica pura, sin Android. */
object ObdFuel {
    /** Relación aire/combustible ideal de la bencina (kg de aire por kg de bencina). */
    private const val GASOLINE_AIR_FUEL = 14.7

    /** Densidad de la bencina, en gramos por litro. */
    private const val GASOLINE_G_PER_L = 740.0

    /** Bajo esta velocidad (km/h) no se calcula el rendimiento: con el auto casi detenido el número no significa nada. */
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

    /** Rendimiento al instante, en km por litro. Null si el auto va casi detenido, no gasta o falta algún dato. */
    fun kmPerLiter(litersPerHour: Double?, speedKmh: Double?): Double? {
        if (litersPerHour == null || litersPerHour <= 0.0 || speedKmh == null || speedKmh < MIN_SPEED_KMH) return null
        return speedKmh / litersPerHour
    }

    /** Agrega al mapa de valores el gasto (`flow`, L/h) y el rendimiento (`kml`, km/L) cuando se pueden calcular. */
    fun addDerived(values: MutableMap<String, Double>) {
        val flow = litersPerHour(values)
        if (flow != null) values["flow"] = flow else values.remove("flow")
        val kml = kmPerLiter(flow, values["speed"])
        if (kml != null) values["kml"] = kml else values.remove("kml")
    }
}
