package cl.tablero.launcher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import cl.tablero.core.DistanceCounter
import cl.tablero.core.GpsFix

/**
 * Suma al kilometraje los km que mide el GPS, para que los avisos de aceite y mantención avancen solos.
 *
 * Tiene que ser un servicio aparte: la pantalla de inicio apaga el GPS al pasar a segundo plano, y casi
 * todo el manejo ocurre con Waze o Maps al frente. Corre como servicio en primer plano (Android lo exige
 * para seguir usando el GPS) y por eso muestra un aviso fijo; se apaga en Ajustes.
 *
 * Solo funciona si el usuario ya anotó un kilometraje: sin un punto de partida no hay nada que sumar.
 * El conteo es una estimación (ver [DistanceCounter]).
 */
class OdometerService : Service(), LocationListener {
    private lateinit var settings: SettingsStore
    private lateinit var locationManager: LocationManager
    private lateinit var counter: DistanceCounter

    /** Kilometraje que dejamos escrito la última vez: si cambia por otro lado, el usuario lo corrigió a mano. */
    private var knownOdometer = 0
    private var savedCarry = 0
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        counter = DistanceCounter(carryMeters = settings.odometerCarryM())
        knownOdometer = settings.odometer()
        savedCarry = counter.carryMeters
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Si nos arrancaron con startForegroundService, Android exige pasar a primer plano de inmediato, aunque enseguida nos apaguemos.
        val foreground = goForeground()
        if (!foreground || !shouldRun()) {
            // Sin primer plano (la app estaba en segundo plano) o sin nada que contar: se reintenta al abrir el inicio.
            if (foreground) {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        settings.setOdometerCarryM(counter.carryMeters)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ---------- GPS ---------- */

    private fun startListening() {
        if (listening) return
        try {
            // Cada 2 segundos basta para medir distancia y gasta menos que el 1 por segundo de la pantalla de inicio.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper())
            listening = true
        } catch (e: SecurityException) {
            stopSelf() // el permiso se retiró
        } catch (e: IllegalArgumentException) {
            stopSelf() // esta radio no tiene GPS
        }
    }

    private fun stopListening() {
        if (!listening) return
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            // sin permiso no había nada que quitar
        }
        listening = false
    }

    override fun onLocationChanged(location: Location) {
        if (!shouldRun()) {
            stopSelf()
            return
        }
        // Si el kilometraje cambió por otro lado, el usuario lo corrigió: los metros sueltos ya no valen.
        val current = settings.odometer()
        if (current != knownOdometer) {
            counter.reset()
            knownOdometer = current
            savedCarry = 0
        }
        val km = counter.onFix(
            GpsFix(
                lat = location.latitude,
                lon = location.longitude,
                timeMs = location.time,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
            ),
        )
        if (km > 0) knownOdometer = settings.addKm(km)
        // Los metros sueltos se guardan cada ~50 m para no perderlos si la radio se apaga de golpe.
        if (km > 0 || kotlin.math.abs(counter.carryMeters - savedCarry) >= 50) {
            settings.setOdometerCarryM(counter.carryMeters)
            savedCarry = counter.carryMeters
        }
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {
        counter.resetTrack() // sin GPS no sabemos qué pasó: al volver se toma un punto nuevo como partida
    }

    @Deprecated("Solo se necesita en Android antiguos")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /* ---------- primer plano ---------- */

    private fun shouldRun(): Boolean = settings.autoOdometerOn() && hasLocationPermission(this)

    /** Pasa a primer plano con el aviso fijo. Devuelve false si Android no lo permitió. */
    private fun goForeground(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Kilometraje", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Cuenta los kilómetros que recorres para los avisos de aceite y mantención."
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_maps)
            .setContentTitle("Tablero cuenta tus kilómetros")
            .setContentText("Para avisarte del aceite y las mantenciones. Se apaga en Ajustes.")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "odometer"
        private const val NOTIFICATION_ID = 41

        fun hasLocationPermission(c: Context): Boolean =
            Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Enciende el servicio si corresponde (hay kilometraje anotado, el conteo está activo y hay permiso de GPS). Hay que llamarlo con la app visible. */
        fun startIfNeeded(c: Context) {
            val settings = SettingsStore(c)
            if (!settings.autoOdometerOn() || !hasLocationPermission(c)) return
            val intent = Intent(c, OdometerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent) else c.startService(intent)
            } catch (e: Exception) {
                // Android no permitió arrancarlo ahora: se reintenta la próxima vez que se abra el inicio.
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, OdometerService::class.java))
        }
    }
}
