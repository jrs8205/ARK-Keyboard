package org.jarsi.ark.settings

/**
 * Asetusten oletusarvot ja arvojen laillistus. Avaimet, tyypit ja
 * oletukset ovat samat, joita näppäimistöpalvelu lukee; mallien,
 * avainten ja taittojen avaimilla ei ole oletusta.
 *
 * Oletukset kirjoitetaan asetustiedostoon avauksessa (vain puuttuvat),
 * kuten aiempi asetuslista teki: varmuuskopio sisältää silloin kaikki
 * arvot, ja kieliriippuvat oletukset lukittuvat ensimmäisen avauksen
 * kieleen.
 */
object SettingsDefaults {

    const val PREF_COLLAPSED = "asetukset_taitetut"

    val SECTION_IDS = listOf(
        "ulkoasu", "kirjoittaminen", "ehdotukset", "kaannos", "sanelu", "tekoaly", "varmuuskopio",
    )

    fun defaults(finnish: Boolean): Map<String, Any> = mapOf(
        "teema" to "jarjestelma",
        "korkeus" to 100,
        "aanet" to false,
        "varina" to true,
        "esikatselu" to true,
        "numerorivi" to true,
        "kaksoisvali_piste" to true,
        "ehdotukset" to true,
        "ehdotus_valilyonti" to true,
        "ehdotus_yleiset" to finnish,
        "automaattikorjaus" to finnish,
        "kaannos_muisti" to 30,
        "sanelu_hiljaisuus" to 5,
        "sanelu_moottori" to "jarjestelma",
        "ai_palvelu" to "claude",
    )

    /** Oletukset niille avaimille, joita [existing] ei vielä sisällä. */
    fun missingDefaults(existing: Map<String, *>, finnish: Boolean): Map<String, Any> =
        defaults(finnish).filterKeys { it !in existing }

    /** Poistetun paikallisen tunnistuksen vanha valinta palautuu oletukseen. */
    fun legalDictationEngine(value: String?): String =
        if (value == "openai") "openai" else "jarjestelma"

    /** Taustakuvan värit vaativat Android 12:n paletin; tuntematon arvo palautuu oletukseen. */
    fun legalTheme(value: String?, dynamicAvailable: Boolean): String = when (value) {
        "vaalea", "tumma", "musta" -> value
        "taustakuva" -> if (dynamicAvailable) value else "jarjestelma"
        else -> "jarjestelma"
    }

    /** Taitettujen osioiden joukko tallennettavaksi: vain tunnetut osiot. */
    fun encodeCollapsed(ids: Set<String>): Set<String> = ids.filter { it in SECTION_IDS }.toSet()
}
