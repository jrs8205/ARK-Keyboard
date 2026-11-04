package org.jarsi.ark.data

import android.content.SharedPreferences

/**
 * Muistinvarainen asetustiedosto testeihin. Kuuntelijat kutsutaan heti
 * kirjoituksen sisällä kuten Androidissa pääsäikeellä, jolloin
 * ilmoituksen aikana tehty luku näkee saman tilan kuin oikealla laitteella.
 */
class FakePrefs : SharedPreferences {

    private val values = HashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            val changed = mutableListOf<String>()
            if (clear) {
                changed += values.keys
                values.clear()
            }
            pending.forEach { (key, value) ->
                val had = values.containsKey(key)
                if (value == null) {
                    if (had) {
                        values.remove(key)
                        changed += key
                    }
                } else if (!had || values[key] != value) {
                    values[key] = value
                    changed += key
                }
            }
            changed.forEach { key -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@FakePrefs, key) } }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
