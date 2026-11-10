package it.federicoliva.spotifybridge

import java.text.Normalizer

/** One album from a Spotify search result, reduced to what matching needs. */
data class Album(val id: String, val name: String, val artists: List<String>)

/** Tags read from the audio file that Mornye hands over. */
data class Tags(val artist: String?, val album: String?, val title: String?, val genre: String? = null)

object Matcher {

    // Edition suffixes that differ between the file's tags and Spotify's catalog.
    private val EDITION = Regex(
        """\s*[(\[][^)\]]*(remaster|deluxe|edition|expanded|anniversary|version|bonus|mono|stereo)[^)\]]*[)\]]""" +
            """|\s+-\s+.*(remaster|deluxe|edition|expanded|anniversary|version).*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Case, accents and punctuation folded; edition suffixes kept. */
    fun clean(s: String): String {
        val ascii = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun normalize(s: String): String = clean(EDITION.replace(s, ""))

    /** First artist only: tags often hold "A; B" or "A, B" while Spotify lists them separately. */
    fun primaryArtist(artist: String): String = artist.split(';', ',', '/', '&').first().trim()

    fun albumQuery(tags: Tags): String? {
        val album = tags.album?.takeIf { it.isNotBlank() } ?: return null
        val artist = tags.artist?.takeIf { it.isNotBlank() }?.let(::primaryArtist).orEmpty()
        return "$artist ${EDITION.replace(album, "")}".trim()
    }

    fun trackQuery(tags: Tags): String? {
        val title = tags.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = tags.artist?.takeIf { it.isNotBlank() }?.let(::primaryArtist).orEmpty()
        return "$artist $title".trim()
    }

    /**
     * Best album for the tags, or null when nothing is a credible match.
     * The artist must match; the album name must match exactly or by containment
     * (after stripping edition suffixes), and an exact name wins over containment.
     */
    fun pick(tags: Tags, candidates: List<Album>): Album? {
        val wantAlbum = tags.album?.let(::normalize).orEmpty()
        val wantExact = tags.album?.let(::clean).orEmpty()
        val wantArtist = tags.artist?.let { normalize(primaryArtist(it)) }.orEmpty()
        return candidates
            .map { it to score(wantAlbum, wantExact, wantArtist, it) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(wantAlbum: String, wantExact: String, wantArtist: String, c: Album): Int {
        val artists = c.artists.map(::normalize)
        val artistScore = when {
            wantArtist.isEmpty() -> 1
            artists.any { it == wantArtist } -> 2
            artists.any { it.contains(wantArtist) || wantArtist.contains(it) } -> 1
            else -> return 0
        }
        val name = normalize(c.name)
        val albumScore = when {
            wantAlbum.isEmpty() -> return 0
            // Same edition as the file beats another edition of the same album.
            clean(c.name) == wantExact -> 5
            name == wantAlbum -> 4
            name.contains(wantAlbum) || wantAlbum.contains(name) -> 2
            else -> return 0
        }
        return albumScore + artistScore
    }
}

/** Decides which files are podcast episodes, to be played locally instead of opened in Spotify. */
object Podcast {

    fun keywords(setting: String?): List<String> =
        setting.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * True when the genre tag says podcast, or the file's location contains one of the
     * user's keywords (case-insensitive). [location] is the decoded content URI: Mornye's
     * FileProvider and SAF document URIs both carry the real path.
     */
    fun matches(genre: String?, location: String, keywords: List<String>): Boolean {
        if (genre?.contains("podcast", ignoreCase = true) == true) return true
        return keywords.any { location.contains(it, ignoreCase = true) }
    }
}
