package it.federicoliva.spotifybridge

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Launcher screen: Spotify app credentials plus a manual lookup to check them. */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SpotifyApi.PREFS, Context.MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun text(s: String) = TextView(this).apply { text = s; setPadding(0, pad / 2, 0, pad / 4) }
        fun field(hint: String, value: String?, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint
            setText(value)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        }

        root.addView(text(
            "Crea un'app su developer.spotify.com (Web API, redirect URI qualsiasi, es. " +
                "http://127.0.0.1:8888/callback) e incolla qui Client ID e Client Secret.\n\n" +
                "In Mornye: Impostazioni › Riproduzione › Library › Player esterno. " +
                "Al primo Play scegli Spotify Bridge come app predefinita.",
        ))
        val id = field("Client ID", prefs.getString(SpotifyApi.KEY_CLIENT_ID, ""))
        val secret = field("Client Secret", prefs.getString(SpotifyApi.KEY_CLIENT_SECRET, ""), secret = true)
        val status = text("")
        root.addView(id); root.addView(secret)
        root.addView(Button(this).apply {
            text = "Salva"
            setOnClickListener {
                prefs.edit()
                    .putString(SpotifyApi.KEY_CLIENT_ID, id.text.toString().trim())
                    .putString(SpotifyApi.KEY_CLIENT_SECRET, secret.text.toString().trim())
                    .apply()
                SpotifyApi.clearToken(prefs)
                status.text = "Salvato"
            }
        })

        root.addView(text("Prova una ricerca"))
        val artist = field("Artista", "Daft Punk")
        val album = field("Album", "Random Access Memories")
        root.addView(artist); root.addView(album)
        root.addView(Button(this).apply {
            text = "Cerca e apri in Spotify"
            setOnClickListener {
                status.text = "Cerco…"
                val tags = Tags(artist.text.toString(), album.text.toString(), null)
                Thread {
                    val msg = try {
                        Bridge.resolve(SpotifyApi(prefs), tags)?.let {
                            Bridge.openAlbum(applicationContext, it.id)
                            "Trovato: ${it.name} — ${it.artists.joinToString()} (${it.id})"
                        } ?: "Nessun album corrispondente"
                    } catch (e: Exception) {
                        "Errore: ${e.message}"
                    }
                    runOnUiThread { status.text = msg }
                }.start()
            }
        })
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })
    }
}
