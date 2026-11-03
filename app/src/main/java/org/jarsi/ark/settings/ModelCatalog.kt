package org.jarsi.ark.settings

import android.content.SharedPreferences
import org.jarsi.ark.data.ChatGptPlan
import org.jarsi.ark.engine.AiRequests
import java.net.URL
import java.util.concurrent.ExecutorService
import javax.net.ssl.HttpsURLConnection

/**
 * AI-mallien lista palvelun Models-rajapinnasta käyttäjän omalla
 * avaimella, jotta uudet mallit näkyvät ilman sovelluspäivitystä.
 * Haku tehdään [executor]-säikeessä ja tulos toimitetaan samassa
 * säikeessä; kutsuja vaihtaa pääsäikeeseen ja tarkistaa elinkaaren.
 */
class ModelCatalog(private val executor: ExecutorService) {

    /** Mallilista tai syy, miksi sitä ei saatu (väärä avain erottuu verkko-ongelmasta). */
    data class Result(val models: List<Pair<String, String>>?, val error: String?)

    fun fetch(
        url: String,
        apiKey: String,
        connectionError: String,
        authorize: (HttpsURLConnection, String) -> Unit,
        parse: (String) -> List<Pair<String, String>>,
        deliver: (Result) -> Unit,
    ) {
        executor.execute {
            var error: String? = null
            val models = try {
                val connection = URL(url).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 20_000
                    authorize(connection, apiKey)
                    if (connection.responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                            .let(parse)
                    } else {
                        val body = connection.errorStream
                            ?.bufferedReader()?.use { it.readText() }
                        error = AiRequests.parseErrorMessage(body)
                            ?: "HTTP ${connection.responseCode}"
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                error = connectionError
                null
            }
            deliver(Result(models, error))
        }
    }

    /** ChatGPT-tilauksen mallilista tilauksen tokenilla. */
    fun fetchPlan(prefs: SharedPreferences, deliver: (Result) -> Unit) {
        executor.execute {
            val (models, error) = ChatGptPlan.models(prefs)
            deliver(Result(models, error))
        }
    }

    companion object {
        /**
         * Valikon nimet: nopeus- ja hintaluokka auttaa valinnassa, kun
         * mallilista elää eikä hintoja saada rajapinnasta. Tilauksen
         * malleille ei näytetä luokkaa, koska ne eivät maksa erikseen.
         */
        fun labels(models: List<Pair<String, String>>, hints: Boolean): List<String> =
            models.map { model ->
                if (hints) {
                    AiRequests.modelHint(model.first)
                        ?.let { "${model.second} – $it" } ?: model.second
                } else {
                    model.second
                }
            }
    }
}
