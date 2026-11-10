package it.federicoliva.spotifybridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Shared by the "open with" entry point and the test button in settings. */
object Bridge {

    fun readTags(context: Context, uri: Uri): Tags {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val albumArtist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            return Tags(
                artist = albumArtist?.takeIf { it.isNotBlank() } ?: artist,
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
            )
        } finally {
            r.release()
        }
    }

    /** Spotify album for the tags: album search first, then the album of a matching track. */
    fun resolve(api: SpotifyApi, tags: Tags): Album? {
        Matcher.albumQuery(tags)?.let { q -> Matcher.pick(tags, api.searchAlbums(q))?.let { return it } }
        Matcher.trackQuery(tags)?.let { q ->
            val albums = api.searchTrackAlbums(q)
            // With no album tag the artist alone decides: take the first track album by that artist.
            return if (tags.album.isNullOrBlank()) {
                val artist = tags.artist?.let { Matcher.normalize(Matcher.primaryArtist(it)) }.orEmpty()
                albums.firstOrNull { a -> artist.isEmpty() || a.artists.any { Matcher.normalize(it) == artist } }
            } else {
                Matcher.pick(tags, albums)
            }
        }
        return null
    }

    fun openAlbum(context: Context, albumId: String) =
        open(context, Uri.parse("spotify:album:$albumId"), Uri.parse("https://open.spotify.com/album/$albumId"))

    /** Last resort: Spotify's own search screen with whatever the tags say. */
    fun openSearch(context: Context, tags: Tags) {
        val q = listOfNotNull(tags.artist?.let(Matcher::primaryArtist), tags.album ?: tags.title)
            .filter { it.isNotBlank() }.joinToString(" ")
        open(context, Uri.parse("spotify:search:${Uri.encode(q)}"), Uri.parse("https://open.spotify.com/search/${Uri.encode(q)}"))
    }

    private fun open(context: Context, appUri: Uri, webUri: Uri) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, appUri).setPackage(SPOTIFY).addFlags(flags))
        } catch (e: ActivityNotFoundException) {
            // Spotify not installed: the browser (or Spotify via app links) takes the web URL.
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(flags))
        }
    }

    /**
     * Plays the file in a regular player: the one chosen in settings, else Android's
     * chooser without this app and without Mornye (opening a file "with Mornye"
     * switches its Library back to the internal player).
     */
    fun playLocally(activity: Activity, uri: Uri, mime: String, player: String?) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!player.isNullOrBlank()) {
            try {
                activity.startActivity(Intent(view).setPackage(player)); return
            } catch (e: ActivityNotFoundException) {
                // Player uninstalled since it was chosen: fall through to the chooser.
            }
        }
        val excluded = activity.packageManager.queryIntentActivities(view, 0)
            .map { it.activityInfo }
            .filter { it.packageName == activity.packageName || it.packageName == MORNYE }
            .map { ComponentName(it.packageName, it.name) }
        activity.startActivity(
            Intent.createChooser(view, "Riproduci il podcast con")
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    /** Apps that can play audio, for the podcast player picker in settings. */
    fun audioPlayers(context: Context): List<Pair<String, String>> {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://probe/x.mp3"), "audio/mpeg")
        val pm = context.packageManager
        return pm.queryIntentActivities(probe, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName && it.first != MORNYE }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private const val SPOTIFY = "com.spotify.music"
    private const val MORNYE = "com.zarz.mornye"
}
