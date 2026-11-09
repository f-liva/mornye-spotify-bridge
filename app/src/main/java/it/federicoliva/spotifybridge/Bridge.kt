package it.federicoliva.spotifybridge

import android.content.ActivityNotFoundException
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

    private const val SPOTIFY = "com.spotify.music"
}
