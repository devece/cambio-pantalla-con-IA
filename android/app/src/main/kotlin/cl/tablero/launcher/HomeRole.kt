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
