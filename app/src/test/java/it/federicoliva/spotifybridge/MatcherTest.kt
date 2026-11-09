package it.federicoliva.spotifybridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatcherTest {

    private val ram = Album("ram", "Random Access Memories", listOf("Daft Punk"))
    private val ramDrumless = Album("drumless", "Random Access Memories (Drumless Edition)", listOf("Daft Punk"))
    private val ramCover = Album("cover", "Vitamin String Quartet Performs Daft Punk's Random Access Memories", listOf("Vitamin String Quartet"))
    private val discovery = Album("disc", "Discovery", listOf("Daft Punk"))

    @Test fun exactNameBeatsContainment() {
        val tags = Tags("Daft Punk", "Random Access Memories", null)
        assertEquals("ram", Matcher.pick(tags, listOf(ramCover, ramDrumless, ram))?.id)
    }

    @Test fun wrongArtistIsRejected() {
        val tags = Tags("Daft Punk", "Random Access Memories", null)
        assertNull(Matcher.pick(tags, listOf(ramCover)))
    }

    @Test fun remasterSuffixInTagsStillMatches() {
        val tags = Tags("Pink Floyd", "The Dark Side of the Moon (2011 Remastered Version)", null)
        val dsotm = Album("dsotm", "The Dark Side of the Moon", listOf("Pink Floyd"))
        assertEquals("dsotm", Matcher.pick(tags, listOf(dsotm))?.id)
    }

    @Test fun multiArtistTagUsesPrimary() {
        val tags = Tags("Daft Punk; Pharrell Williams", "Random Access Memories", null)
        assertEquals("ram", Matcher.pick(tags, listOf(discovery, ram))?.id)
    }

    @Test fun accentsAndAmpersandNormalize() {
        assertEquals(Matcher.normalize("Beyoncé & Jay-Z"), Matcher.normalize("Beyonce and Jay Z"))
    }

    @Test fun unrelatedAlbumIsRejected() {
        val tags = Tags("Daft Punk", "Homework", null)
        assertNull(Matcher.pick(tags, listOf(ram, discovery)))
    }

    @Test fun missingAlbumTagGivesNoAlbumQuery() {
        assertNull(Matcher.albumQuery(Tags("Daft Punk", "", "Get Lucky")))
        assertEquals("Daft Punk Get Lucky", Matcher.trackQuery(Tags("Daft Punk", "", "Get Lucky")))
    }

    @Test fun albumQueryDropsEditionAndExtraArtists() {
        assertEquals(
            "Fleetwood Mac Rumours",
            Matcher.albumQuery(Tags("Fleetwood Mac, Someone", "Rumours (Super Deluxe)", null)),
        )
    }

    @Test fun taggedEditionPicksThatEdition() {
        val tags = Tags("Daft Punk", "Random Access Memories (Drumless Edition)", null)
        assertEquals("drumless", Matcher.pick(tags, listOf(ram, ramDrumless))?.id)
    }
}
