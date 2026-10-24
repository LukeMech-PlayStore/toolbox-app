package org.lukemech.lmtoolbox.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferenceHelper {

    // Default file name for general app settings
    private const val DEFAULT_PREFS_NAME = "lmtoolbox_shared_prefs"

    /**
     * Internal helper to get the SharedPreferences instance.
     * Allows specifying a custom file name.
     */
    private fun getPrefs(context: Context, fileName: String): SharedPreferences {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    /** Every value stored (e.g. to tell whether any setting still points at something). */
    fun values(context: Context, fileName: String = DEFAULT_PREFS_NAME): Collection<Any?> = getPrefs(context, fileName).all.values

    /**
     * Generic method to save a value to SharedPreferences.
     *
     * @param fileName The name of the preference file. Defaults to "lmtoolbox_shared_prefs".
     */
    fun put(context: Context, key: String, value: Any?, fileName: String = DEFAULT_PREFS_NAME) {
        val prefs = getPrefs(context, fileName)
        prefs.edit {
            when (value) {
                is String? -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported preference type")
            }
        }
    }

    /**
     * Generic method to retrieve a value from SharedPreferences.
     *
     * @param fileName The name of the preference file. Defaults to "lmtoolbox_shared_prefs".
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(context: Context, key: String, default: T, fileName: String = DEFAULT_PREFS_NAME): T {
        val prefs = getPrefs(context, fileName)
        return when (default) {
            is String -> prefs.getString(key, default) as T
            is Int -> prefs.getInt(key, default) as T
            is Boolean -> prefs.getBoolean(key, default) as T
            is Float -> prefs.getFloat(key, default) as T
            is Long -> prefs.getLong(key, default) as T
            is Set<*> -> prefs.getStringSet(key, default as? Set<String>) as T
            else -> error("Unsupported preference type")
        }
    }

    /**
     * Helper to save a list of strings (joined by comma).
     */
    fun putStringList(context: Context, key: String, list: List<String>, fileName: String = DEFAULT_PREFS_NAME) {
        put(context, key, list.joinToString(","), fileName)
    }

    /**
     * Helper to retrieve a list of strings (split by comma).
     */
    fun getStringList(context: Context, key: String, fileName: String = DEFAULT_PREFS_NAME): List<String> {
        val joined = get(context, key, "", fileName)
        return if (joined.isEmpty()) emptyList() else joined.split(",")
    }

    /**
     * Whether [key] is set at all (not just its default).
     */
    fun contains(context: Context, key: String, fileName: String = DEFAULT_PREFS_NAME): Boolean =
        getPrefs(context, fileName).contains(key)

    /**
     * Removes a specific key from the preferences.
     */
    fun remove(context: Context, key: String, fileName: String = DEFAULT_PREFS_NAME) {
        getPrefs(context, fileName).edit { remove(key) }
    }

    /**
     * Clears all data from the specified preference file.
     */
    fun clear(context: Context, fileName: String = DEFAULT_PREFS_NAME) {
        getPrefs(context, fileName).edit { clear() }
    }
}