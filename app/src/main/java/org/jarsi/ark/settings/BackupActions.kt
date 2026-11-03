package org.jarsi.ark.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.jarsi.ark.R
import org.jarsi.ark.data.Backup
import org.jarsi.ark.data.BackupCodec
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import java.util.concurrent.ExecutorService

/**
 * Varmuuskopion vienti ja tuonti taustasäikeessä. [context] on
 * sovelluskonteksti, jotta työ ei pidä aktiviteettia hengissä; tulos
 * kerrotaan [toast]-kutsulla, jonka kutsuja vie pääsäikeeseen.
 * Tuonnin jälkeen asetusrivit päivittyvät asetustiedoston kuuntelijasta.
 * Tietokanta on sovelluksen yhteinen instanssi, jota ei koskaan suljeta:
 * näppäimistöpalvelu käyttää samaa yhteyttä prosessin loppuun asti.
 */
class BackupActions(
    private val context: Context,
    private val executor: ExecutorService,
    private val toast: (String) -> Unit,
) {
    private var database: LearnedDatabase? = null

    // Suoritetaan taustasäikeessä; tietokanta avataan vasta tarvittaessa.
    private fun openDao() =
        (database ?: LearnedDatabase.create(context).also { database = it }).dao()

    fun export(uri: Uri) {
        executor.execute {
            try {
                val dao = openDao()
                val backup = Backup(
                    words = dao.allWords(),
                    bigrams = dao.allBigrams(),
                    trigrams = dao.allTrigrams(),
                    // Kuvaleikkeet jäävät pois: ne ovat tiedostoja, eivät dataa.
                    clips = dao.allClips().filter { it.pinned && it.text != null },
                    // Asetukset mukaan, jotta näppäimistö toimii uudella
                    // laitteella samoin; API-avaimet suodattuvat pois.
                    settings = BackupCodec.exportableSettings(
                        PreferenceManager.getDefaultSharedPreferences(context).all
                    ),
                )
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(BackupCodec.encode(backup).toByteArray(Charsets.UTF_8))
                } ?: error("Tiedosto ei auennut")
                toast(summary(R.string.varmuuskopio_viety, backup))
            } catch (e: Exception) {
                toast(context.getString(R.string.varmuuskopio_virhe))
            }
        }
    }

    fun import(uri: Uri) {
        executor.execute {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Tiedosto ei auennut")
                val backup = BackupCodec.decode(text)
                val dao = openDao()
                dao.upsertWords(BackupCodec.mergeWords(dao.allWords(), backup.words))
                dao.upsertBigrams(BackupCodec.mergeBigrams(dao.allBigrams(), backup.bigrams))
                dao.upsertTrigrams(
                    BackupCodec.mergeTrigrams(dao.allTrigrams(), backup.trigrams)
                )
                BackupCodec.newClips(dao.allClips(), backup.clips, System.currentTimeMillis())
                    .forEach { dao.upsertClip(it) }
                if (backup.settings.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                        backup.settings.forEach { (key, value) ->
                            when (value) {
                                is Boolean -> putBoolean(key, value)
                                is Int -> putInt(key, value)
                                is Long -> putLong(key, value)
                                is String -> putString(key, value)
                            }
                        }
                    }
                }
                LearnedDataStamp.bump()
                toast(summary(R.string.varmuuskopio_tuotu, backup))
            } catch (e: Exception) {
                toast(context.getString(R.string.varmuuskopio_virhe))
            }
        }
    }

    private fun summary(messageRes: Int, backup: Backup): String = context.getString(
        messageRes,
        context.resources.getQuantityString(
            R.plurals.varmuuskopio_sanoja, backup.words.size, backup.words.size
        ),
        context.resources.getQuantityString(
            R.plurals.varmuuskopio_leikkeita, backup.clips.size, backup.clips.size
        ),
    )
}
