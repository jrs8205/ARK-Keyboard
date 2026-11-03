package org.jarsi.ark.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import org.jarsi.ark.R

/**
 * Tukiosion ja versiorivin linkit. PayPal avataan ensin PayPal-sovellukseen,
 * jos se on asennettu: pelkkä linkki tarjoaisi vain valinnan sovelluksen ja
 * selaimen välillä laitteilla, jotka eivät ole vahvistaneet sovellusta
 * paypal.me-osoitteelle. Ilman sovellusta, tai jos se kieltäytyy, linkki
 * avataan tavallisesti.
 */
object SupportLinks {

    const val SPONSOR_URL = "https://github.com/sponsors/jrs8205"
    const val COFFEE_URL = "https://buymeacoffee.com/jrs8205"
    const val PAYPAL_URL = "https://paypal.me/JarsiS"
    const val REPO_URL = "https://github.com/jrs8205/ARK-Keyboard"
    const val PAYPAL_PACKAGE = "com.paypal.android.p2pmobile"

    /** Yksi avausyritys: osoite ja mahdollinen pakettirajaus. */
    data class Target(val url: String, val packageName: String?)

    /** Avausyritykset järjestyksessä; ensimmäinen onnistunut riittää. */
    fun targets(url: String): List<Target> =
        if (url == PAYPAL_URL) {
            listOf(Target(url, PAYPAL_PACKAGE), Target(url, null))
        } else {
            listOf(Target(url, null))
        }

    /** Avaa linkin; jos mikään sovellus ei ota sitä vastaan, kerrotaan toastilla. */
    fun open(context: Context, url: String) {
        val opened = targets(url).any { target ->
            val intent = Intent(Intent.ACTION_VIEW, target.url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            target.packageName?.let { intent.setPackage(it) }
            runCatching { context.startActivity(intent) }.isSuccess
        }
        if (!opened) {
            Toast.makeText(context, R.string.linkki_ei_sovellusta, Toast.LENGTH_LONG).show()
        }
    }
}
