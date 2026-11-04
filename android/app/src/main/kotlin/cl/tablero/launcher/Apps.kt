package cl.tablero.launcher

import android.content.Intent
import android.content.pm.PackageManager

/** Busca cómo abrir una app instalada, aun si la radio no la marca como app "normal" de escritorio. */
object Apps {
    fun launchIntent(pm: PackageManager, pkg: String): Intent? {
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
        if (android.os.Build.VERSION.SDK_INT >= 21) pm.getLeanbackLaunchIntentForPackage(pkg)?.let { return it }
        // Cualquier pantalla principal de esa app (radios con apps sin categoría LAUNCHER).
        val main = Intent(Intent.ACTION_MAIN).setPackage(pkg)
        val info = pm.queryIntentActivities(main, 0).firstOrNull() ?: return null
        return Intent(Intent.ACTION_MAIN).setClassName(info.activityInfo.packageName, info.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isInstalled(pm: PackageManager, pkg: String): Boolean = launchIntent(pm, pkg) != null
}
