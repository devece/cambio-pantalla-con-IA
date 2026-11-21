package cl.tablero.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

const val REQ_HOME_ROLE = 9101

/** Alias de actividad declarado desactivado en el manifiesto (ver manifest-fragment.xml). */
private const val ALIAS_INICIO = "cl.tablero.launcher.InicioAlterno"

private fun aliasInicio(context: Context) = ComponentName(context.packageName, ALIAS_INICIO)

private fun fijarAlias(context: Context, activo: Boolean) {
    context.packageManager.setComponentEnabledSetting(
        aliasInicio(context),
        if (activo) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

/**
 * Hace que Android vuelva a preguntar qué app usar como pantalla de inicio.
 * Devuelve true si se abrió algún diálogo o lista.
 */
fun requestHome(activity: Activity): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE)
                return true
            }
        } catch (_: Throwable) {
            // Cae al método del componente alterno.
        }
    }
    return try {
        // Con dos candidatos HOME de la misma app, el sistema invalida la elección guardada y muestra la lista.
        fijarAlias(activity, true)
        activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        true
    } catch (_: Throwable) {
        releaseAlternateHome(activity)
        false
    }
}

/** Vuelve a desactivar el componente alterno. Seguro de llamar siempre. */
fun releaseAlternateHome(context: Context) {
    try {
        fijarAlias(context, false)
    } catch (_: Throwable) {
    }
}
