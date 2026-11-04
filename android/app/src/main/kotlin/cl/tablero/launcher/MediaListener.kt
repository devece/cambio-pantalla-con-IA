package cl.tablero.launcher

import android.service.notification.NotificationListenerService

/**
 * No hace nada por sí solo: Android exige tener un "oyente de notificaciones" autorizado por el usuario para dejar
 * que una app lea qué canción suena (Spotify, YouTube Music, etc.). WebLauncher lo usa para pedir las sesiones de música.
 */
class MediaListener : NotificationListenerService()
