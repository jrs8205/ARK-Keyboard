package org.jarsi.ark.keyboard

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Työkalurivin napit tallennustunnisteineen; enumin järjestys on oletusjärjestys. */
enum class ToolbarTool(val id: String) {
    ARROWS("nuolet"),
    WEB("www"),
    MIC("mikrofoni"),
    EMOJI("emojit"),
    CLIPBOARD("leikepoyta"),
    TRANSLATE("kaannos"),
    UNDO("peruutus"),
    ONE_HAND("yksi_kasi"),
    SETTINGS("asetukset");

    companion object {
        fun byId(id: String): ToolbarTool? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Työkalurivin nappien järjestys ja piilotukset. Käyttäjän valinta
 * sovitetaan aina nykyiseen nappivalikoimaan: tuntemattomat ja
 * kaksoiskappaleet siivotaan, uudet napit palaavat järjestyksen loppuun
 * näkyvinä (viimeisenä olevan asetusnapin eteen), eikä asetusnappia voi
 * piilottaa, jotta asetuksiin pääsee aina.
 */
object ToolbarOrder {

    const val PREF_KEY = "tyokalurivi"

    val default: List<ToolbarTool> = ToolbarTool.entries.toList()

    // Peruutus saa näkyviin asetuksista; Gboardissakin se on valinnainen.
    private val defaultHidden = setOf(ToolbarTool.UNDO)

    data class Config(val order: List<ToolbarTool>, val hidden: Set<ToolbarTool>) {
        val visible: List<ToolbarTool> get() = order.filter { it !in hidden }
    }

    fun serialize(order: List<ToolbarTool>, hidden: Set<ToolbarTool>): String =
        JSONObject()
            .put("jarjestys", JSONArray(order.map { it.id }))
            .put("piilotetut", JSONArray(hidden.map { it.id }))
            .toString()

    fun load(saved: String?): Config {
        if (saved == null) return Config(default, defaultHidden)
        val parsed = try {
            val json = JSONObject(saved)
            fun ids(name: String): List<String> {
                val array = json.optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).map { array.getString(it) }
            }
            ids("jarjestys") to ids("piilotetut")
        } catch (e: JSONException) {
            return Config(default, defaultHidden)
        }
        val kept = parsed.first.mapNotNull(ToolbarTool::byId).distinct()
        val missing = default.filter { it !in kept }
        val order = if (kept.lastOrNull() == ToolbarTool.SETTINGS) {
            kept.dropLast(1) + missing + ToolbarTool.SETTINGS
        } else {
            kept + missing
        }
        val saved = parsed.second.mapNotNull(ToolbarTool::byId).toSet() - ToolbarTool.SETTINGS
        // Yhden käden napin puuttuminen kertoo vanhasta tallennuksesta: oletus koskee
        // sitäkin kerran. Sen jälkeen käyttäjän oma valinta pysyy.
        val hidden = if (ToolbarTool.ONE_HAND in kept) saved else saved + defaultHidden
        return Config(order, hidden)
    }
}
