package it.federicoliva.spotifybridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastTest {

    private val mornyeUri =
        "content://com.zarz.mornye.fileProvider.com.crazecoder.openfile/root/storage/emulated/0/Podcasts/Ep 12.mp3"
    private val musicUri =
        "content://com.zarz.mornye.fileProvider.com.crazecoder.openfile/root/storage/emulated/0/Music/Daft Punk/01.flac"

    @Test fun genreTagWins() = assertTrue(Podcast.matches("Podcast", musicUri, emptyList()))

    @Test fun genreIsCaseInsensitiveAndPartial() = assertTrue(Podcast.matches("Tech podcasts", musicUri, emptyList()))

    @Test fun folderKeywordMatches() = assertTrue(Podcast.matches(null, mornyeUri, listOf("podcasts")))

    @Test fun safDocumentPathMatches() =
        assertTrue(Podcast.matches(null, "content://com.android.externalstorage.documents/document/primary:AntennaPod/x.mp3", listOf("AntennaPod")))

    @Test fun musicStaysMusic() {
        assertFalse(Podcast.matches("Electronic", musicUri, listOf("Podcasts")))
        assertFalse(Podcast.matches(null, musicUri, emptyList()))
    }

    @Test fun keywordSettingIsTrimmedAndBlankDropped() =
        assertEquals(listOf("Podcasts", "AntennaPod"), Podcast.keywords(" Podcasts, ,AntennaPod "))
}
