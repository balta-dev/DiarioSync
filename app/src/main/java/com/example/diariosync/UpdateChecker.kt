import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/balta-dev/DiarioSync/releases/latest"

    suspend fun hayNuevaVersion(versionActual: String): Triple<Boolean, String?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(API_URL).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Triple(false, null, null)

                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v")

                // BUSCAR EL APK EN LOS ASSETS
                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                val esNueva = tagName != versionActual
                Triple(esNueva, if (esNueva) downloadUrl else null, if (esNueva) tagName else null)
            } catch (e: Exception) {
                Triple(false, null, null)
            }
        }
    }
}