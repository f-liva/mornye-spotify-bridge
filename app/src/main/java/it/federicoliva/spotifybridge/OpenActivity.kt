package it.federicoliva.spotifybridge

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Registered as an audio player. Mornye (Library > external player) opens a downloaded
 * file here with ACTION_VIEW; we read its tags and open the same album in Spotify.
 */
class OpenActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish(); return
        }
        val app = applicationContext
        val prefs = getSharedPreferences(SpotifyApi.PREFS, Context.MODE_PRIVATE)
        Thread {
            val tags = try {
                Bridge.readTags(app, uri)
            } catch (e: Exception) {
                Log.w(TAG, "tags unreadable for $uri", e)
                done("Non riesco a leggere i tag del file"); return@Thread
            }
            Log.i(TAG, "tags: $tags")
            val keywords = Podcast.keywords(prefs.getString(SpotifyApi.KEY_PODCAST_KEYWORDS, null))
            if (Podcast.matches(tags.genre, Uri.decode(uri.toString()), keywords)) {
                Log.i(TAG, "podcast, playing locally")
                val mime = intent.type ?: contentResolver.getType(uri) ?: "audio/*"
                main.post {
                    try {
                        Bridge.playLocally(this, uri, mime, prefs.getString(SpotifyApi.KEY_PODCAST_PLAYER, null))
                    } catch (e: Exception) {
                        Log.w(TAG, "local playback failed", e)
                        toast("Nessun player per il podcast: ${e.message}")
                    }
                    finish()
                }
                return@Thread
            }
            main.post { toast("Cerco l'album su Spotify…") }
            try {
                val album = Bridge.resolve(SpotifyApi(prefs), tags)
                if (album != null) {
                    Log.i(TAG, "matched ${album.id} ${album.name} ${album.artists}")
                    Bridge.openAlbum(app, album.id)
                    done(null)
                } else {
                    Bridge.openSearch(app, tags)
                    done("Album non trovato con certezza: apro la ricerca")
                }
            } catch (e: SpotifyApi.AuthException) {
                Bridge.openSearch(app, tags)
                done("${e.message}: apro la ricerca. Configura l'app Spotify Bridge.")
            } catch (e: Exception) {
                Log.w(TAG, "lookup failed", e)
                Bridge.openSearch(app, tags)
                done("Errore Spotify (${e.message}): apro la ricerca")
            }
        }.start()
    }

    private fun done(message: String?) = main.post {
        message?.let(::toast)
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    companion object { private const val TAG = "SpotifyBridge" }
}
