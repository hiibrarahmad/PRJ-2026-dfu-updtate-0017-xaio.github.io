package io.xaio.ota.storage

import android.content.Context
import com.google.gson.Gson
import io.xaio.ota.AppConfig
import io.xaio.ota.BuildConfig
import io.xaio.ota.model.LatestCatalog
import io.xaio.ota.model.ReleaseCatalogSnapshot
import io.xaio.ota.model.ReleaseHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ReleaseCatalogRepository(private val context: Context) {

    private val gson = Gson()
    private val httpClient = OkHttpClient()

    suspend fun fetch(): Result<ReleaseCatalogSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(AppConfig.otaCachePrefs, Context.MODE_PRIVATE)
            val cachedCatalog = prefs.getString("catalog_json", null)
            val cachedReleases = prefs.getString("releases_json", null)

            val catalogJson = getUrl(BuildConfig.CATALOG_URL)
            val releasesJson = getUrl(BuildConfig.RELEASES_URL)

            prefs.edit()
                .putString("catalog_json", catalogJson)
                .putString("releases_json", releasesJson)
                .putLong("cache_time", System.currentTimeMillis())
                .apply()

            ReleaseCatalogSnapshot(
                latest = gson.fromJson(catalogJson, LatestCatalog::class.java),
                history = gson.fromJson(releasesJson, ReleaseHistory::class.java),
            )
        }.recoverCatching { error ->
            val prefs = context.getSharedPreferences(AppConfig.otaCachePrefs, Context.MODE_PRIVATE)
            val cachedCatalog = prefs.getString("catalog_json", null)
            val cachedReleases = prefs.getString("releases_json", null)
            if (cachedCatalog != null && cachedReleases != null) {
                ReleaseCatalogSnapshot(
                    latest = gson.fromJson(cachedCatalog, LatestCatalog::class.java),
                    history = gson.fromJson(cachedReleases, ReleaseHistory::class.java),
                )
            } else {
                throw error
            }
        }
    }

    private fun getUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} while fetching $url")
            }
            return response.body?.string() ?: error("Empty response body from $url")
        }
    }
}
