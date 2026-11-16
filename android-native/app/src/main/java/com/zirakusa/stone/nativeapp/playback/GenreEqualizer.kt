package com.zirakusa.stone.nativeapp.playback

/**
 * Equalizer by genre: maps the genre tag of a file ("Electronic", "Hard Rock", "Reggaetón"…)
 * to one of Stone's presets. Pure so it can be tested; names match the equalizer's preset cards.
 */
object GenreEqualizer {
    data class Preset(val name: String, val values: List<Int>)

    private val electronic = Preset("Electrónica", listOf(4, 7, 6, -1, -2, 0, 5, 8, 3, 5))
    private val metal = Preset("Heavy metal", listOf(2, 4, 3, 1, 2, 3, 3, 2, 2, 0))
    private val rock = Preset("Rock", listOf(5, 3, 2, 0, 0, 1, 0, 2, 2, 4))
    private val hiphop = Preset("HipHop", listOf(5, 5, 3, 0, -2, -2, 2, 3, 4, 4))
    private val rnb = Preset("R&B", listOf(5, 5, 4, 0, -2, -1, 2, 2, 2, 3))
    private val jazz = Preset("Jazz", listOf(3, 3, 2, 0, -1, -1, 0, 2, 3, 4))
    private val classical = Preset("Clásico", listOf(0, 1, -2, 0, -1, 0, 2, -2, -2, 3))
    private val folk = Preset("Folk", listOf(0, 2, 3, 0, 0, 0, 6, 3, 2, -1))
    private val latin = Preset("Latín", listOf(1, 3, 2, 0, 0, -1, 0, -1, 0, 3))
    private val pop = Preset("Pop", listOf(-1, -1, 0, 1, 1, 2, 2, 1, 0, -1))
    private val dance = Preset("Baile", listOf(5, 5, 4, 2, 1, 3, 4, 3, 2, 0))

    /** First matching rule wins, so "Dance Pop" is dance and "Pop Rock" is rock. */
    private val rules = listOf(
        listOf("metal", "metalcore", "grindcore") to metal,
        listOf("electr", "edm", "techno", "house", "trance", "dubstep", "drum", "bass music", "synth", "electro",
            "phonk", "lo-fi", "lofi", "chill", "ambient", "idm", "breakbeat", "garage") to electronic,
        listOf("hip", "rap", "trap", "drill", "grime") to hiphop,
        listOf("r&b", "rnb", "r and b", "soul", "funk", "neo soul") to rnb,
        listOf("rock", "punk", "grunge", "emo", "alternativ", "indie", "post-hardcore") to rock,
        listOf("reggaeton", "reggaetón", "latin", "latino", "salsa", "bachata", "cumbia", "merengue", "urbano",
            "dembow", "tango", "bolero", "ranchera", "corrido", "regional mexican", "mpb", "samba", "bossa") to latin,
        listOf("jazz", "blues", "swing") to jazz,
        listOf("classic", "clásic", "orchestr", "orquest", "opera", "ópera", "baroque", "barroc", "soundtrack",
            "score", "instrumental") to classical,
        listOf("folk", "country", "acoustic", "acústic", "singer-songwriter", "cantautor") to folk,
        listOf("dance", "disco", "baile", "eurodance") to dance,
        listOf("pop", "k-pop", "kpop", "j-pop") to pop,
    )

    fun presetFor(genre: String?): Preset? {
        val value = genre?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return rules.firstOrNull { (words, _) -> words.any { it in value } }?.second
    }
}
