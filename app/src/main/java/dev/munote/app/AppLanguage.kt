package dev.munote.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguage {
    const val CHINESE = "zh-CN"
    const val ENGLISH = "en"

    private const val PREFS = "munote_preferences"
    private const val KEY_LANGUAGE = "app_language"

    fun applySavedOrDefault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString(KEY_LANGUAGE, null) ?: CHINESE.also {
            prefs.edit().putString(KEY_LANGUAGE, it).apply()
        }
        val desired = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    fun set(context: Context, languageTag: String) {
        val normalized = when (languageTag) {
            ENGLISH -> ENGLISH
            else -> CHINESE
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalized)
        )
    }

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, CHINESE)
            ?: CHINESE
}
