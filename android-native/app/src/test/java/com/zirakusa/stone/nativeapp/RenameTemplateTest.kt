package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.data.library.RenameSource
import com.zirakusa.stone.nativeapp.data.library.RenameTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameTemplateTest {
    private val source = RenameSource("Skyfall", "Adele", "Skyfall OST", "VID_20240101_skyfall", "2012", 7)

    @Test fun rendersPlaceholdersAndPaddedCounter() {
        assertEquals("Adele - Skyfall", RenameTemplate.render("{artist} - {title}", source))
        assertEquals("Episodio 07 (2012)", RenameTemplate.render("Episodio {n:2} ({date})", source))
        assertEquals("VID_20240101_skyfall", RenameTemplate.render("{file}", source))
    }

    @Test fun cleansTechnicalVideoNames() {
        assertEquals("Alan Walker - Faded", RenameTemplate.clean("Alan_Walker_-_Faded_[1080p].mp4"))
        assertEquals("Adele - Skyfall", RenameTemplate.clean("Adele - Skyfall (Official Music Video) [dQw4w9WgXcQ]"))
        assertEquals("Artist Song Name", RenameTemplate.clean("Artist.Song.Name.x264.mkv"))
        // Ordinary names keep their dots and words.
        assertEquals("Mr.Kitty - After Dark", RenameTemplate.clean("Mr.Kitty - After Dark"))
        assertEquals("Hello2World", RenameTemplate.clean("Hello2World"))
    }
}
