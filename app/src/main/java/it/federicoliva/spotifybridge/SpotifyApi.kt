package it.federicoliva.spotifybridge

import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify Web API with the client-credentials flow: no user login, enough for /v1/search.
 * Blocking calls: run them off the main thread.
 */
class SpotifyApi(private val prefs: SharedPreferences) {

    class AuthException(msg: String) : IOException(msg)

    fun searchAlbums(query: String): List<Album> =
        parseAlbums(get("search", query, "album").getJSONObject("albums").getJSONArray("items"))

    /** Albums of the tracks matching the query, for files whose album tag is missing or odd. */
    fun searchTrackAlbums(query: String): List<Album> {
        val items = get("search", query, "track").getJSONObject("tracks").getJSONArray("items")
        return parseAlbums(JSONArray((0 until items.length()).map { items.getJSONObject(it).getJSONObject("album") }))
    }

    private fun get(path: String, query: String, type: String): JSONObject {
        val q = URLEncoder.encode(query, "UTF-8")
        // Development-mode apps get at most 10 results per search (Feb 2026 change).
        val url = URL("https://api.spotify.com/v1/$path?q=$q&type=$type&limit=10")
        var (code, body) = request(url, "GET", "Bearer ${token(false)}", null)
        if (code == 401) {
            val retry = request(url, "GET", "Bearer ${token(true)}", null)
            code = retry.first; body = retry.second
        }
        if (code != 200) throw IOException("Spotify search HTTP $code: ${body.take(200)}")
        return JSONObject(body)
    }

    private fun token(force: Boolean): String {
        val cached = prefs.getString(KEY_TOKEN, null)
        if (!force && cached != null && System.currentTimeMillis() < prefs.getLong(KEY_TOKEN_EXP, 0)) return cached

        val id = prefs.getString(KEY_CLIENT_ID, "").orEmpty()
        val secret = prefs.getString(KEY_CLIENT_SECRET, "").orEmpty()
        if (id.isBlank() || secret.isBlank()) throw AuthException("Client ID / Secret mancanti")

        val basic = Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)
        val (code, body) = request(
            URL("https://accounts.spotify.com/api/token"), "POST", "Basic $basic", "grant_type=client_credentials",
        )
        if (code != 200) throw AuthException("Token Spotify rifiutato (HTTP $code): ${body.take(200)}")
        val json = JSONObject(body)
        val token = json.getString("access_token")
        // One minute of margin on the expiry.
        val exp = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXP, exp).apply()
        return token
    }

    private fun request(url: URL, method: String, auth: String, form: String?): Pair<Int, String> {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Authorization", auth)
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun parseAlbums(items: JSONArray): List<Album> = (0 until items.length()).mapNotNull { i ->
        // Search results can contain null slots.
        val a = items.optJSONObject(i) ?: return@mapNotNull null
        val artists = a.optJSONArray("artists") ?: JSONArray()
        Album(
            a.getString("id"),
            a.optString("name"),
            (0 until artists.length()).map { artists.getJSONObject(it).optString("name") },
        )
    }

    companion object {
        const val PREFS = "spotify"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret"
        const val KEY_PODCAST_KEYWORDS = "podcast_keywords"
        const val KEY_PODCAST_PLAYER = "podcast_player"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_EXP = "token_exp"

        fun clearToken(prefs: SharedPreferences) = prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_EXP).apply()
    }
}
