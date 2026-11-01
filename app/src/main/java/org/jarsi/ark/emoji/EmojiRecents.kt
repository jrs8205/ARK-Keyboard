package org.jarsi.ark.emoji

/**
 * Viimeksi käytetyt emojit ja muistetut ihonvärit. Tilat ovat muuttumattomia
 * kokoelmia, koska ehdotussäie lukee niitä pääsäikeen kirjoittaessa.
 */
class EmojiRecents(savedRecents: String? = null, savedVariants: String? = null) {

    @Volatile
    var recent: List<String> = savedRecents.orEmpty().lines()
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_RECENT)
        private set

    @Volatile
    private var variants: Map<String, String> = savedVariants.orEmpty().lines()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .toMap()

    fun use(emoji: String) {
        recent = (listOf(emoji) + recent.filter { it != emoji }).take(MAX_RECENT)
    }

    /** Näytettävä muoto: muistettu ihonväri tai perusemoji. */
    fun variantFor(base: String): String = variants[base] ?: base

    fun rememberVariant(base: String, variant: String) {
        variants = if (variant == base) variants - base else variants + (base to variant)
    }

    fun savedRecents(): String = recent.joinToString("\n")

    fun savedVariants(): String =
        variants.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    companion object {
        const val MAX_RECENT = 40

        // Aiemman kirjastovalitsimen tallennusmuoto (pilkuin erotettu lista).
        fun fromLegacy(saved: String): EmojiRecents =
            EmojiRecents(saved.split(',').joinToString("\n"))
    }
}
