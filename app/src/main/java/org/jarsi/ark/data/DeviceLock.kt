package org.jarsi.ark.data

import android.content.Context
import android.os.UserManager

/**
 * Puhelimen käynnistyksen jälkeen ennen ensimmäistä avausta sovelluksen
 * tavallinen tallennustila (asetukset, oppimistietokanta) ei ole luettavissa.
 * Näppäimistö käynnistyy silti, jotta lukitusnäytön salasanan voi kirjoittaa.
 */
object DeviceLock {
    fun isUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
}
