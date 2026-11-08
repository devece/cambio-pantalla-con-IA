package cl.tablero.core

import org.json.JSONObject

/** La versión más nueva publicada (el archivo version.json que sube la compilación junto al APK). */
data class UpdateInfo(val versionCode: Int, val versionName: String)

/** Reglas del aviso de actualización, sin Android para poder probarlas en cualquier PC. */
object AppUpdate {
    /** Lee el version.json. Si viene roto o sin número de versión, devuelve null (nunca se avisa por algo dudoso). */
    fun parse(json: String?): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val code = o.optInt("versionCode", 0)
            if (code <= 0) null else UpdateInfo(code, o.optString("versionName", "").take(32))
        } catch (e: Exception) {
            null
        }
    }

    /** Hay que avisar solo si la publicada es más nueva que la instalada y el usuario no la dejó para después. */
    fun shouldOffer(installed: Int, latest: UpdateInfo?, postponed: Int): Boolean =
        latest != null && latest.versionCode > installed && latest.versionCode > postponed
}
