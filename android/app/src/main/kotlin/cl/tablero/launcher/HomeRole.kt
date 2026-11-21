package cl.tablero.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** ¿Tablero ya es la pantalla de inicio de la radio? Desde Android 10 se pregunta por el rol; antes, quién resuelve la acción HOME. */
fun isHomeApp(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        runCatching { return context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

/**
 * Pide de nuevo elegir Tablero como pantalla de inicio. Android 10 o más nuevo: el diálogo oficial del rol de inicio. Antes: se activa
 * un instante el alias `InicioAlterno` (ver el manifiesto) (otro inicio) para que el sistema olvide el que estaba fijado y vuelva a preguntar. Devuelve false si no se pudo.
 */
fun requestHome(activity: android.app.Activity): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        try {
            val intent = activity.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
            activity.startActivityForResult(intent, REQ_HOME_ROLE)
            return true
        } catch (e: Exception) {
            // sigue con el método antiguo
        }
    }
    return try {
        val fake = alternateHome(activity)
        activity.packageManager.setComponentEnabledSetting(fake, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        releaseAlternateHome(activity)
        false
    }
}

/** Vuelve a desactivar el alias `InicioAlterno` (ver el manifiesto). Se llama al volver a Ajustes, para que nunca quede un segundo inicio activo. */
fun releaseAlternateHome(context: Context) {
    runCatching {
        context.packageManager.setComponentEnabledSetting(
            alternateHome(context),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/** El alias de MainActivity que se activa un instante (ver AndroidManifest.xml). */
private fun alternateHome(context: Context) = android.content.ComponentName(context, "cl.tablero.launcher.InicioAlterno")

const val REQ_HOME_ROLE = 9101
