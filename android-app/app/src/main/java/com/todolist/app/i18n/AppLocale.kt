package com.todolist.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * User-selected UI language. [SYSTEM] clears the app-level override and follows the device locale.
 * To add a language: extend this enum, add `values-xx/`, [toLocaleListCompat], [fromStorageValue], and [locales_config.xml].
 */
enum class AppLocale {
    SYSTEM,
    EN,
    ZH_CN,
    ;

    fun toStorageValue(): String =
        when (this) {
            SYSTEM -> STORAGE_SYSTEM
            EN -> STORAGE_EN
            ZH_CN -> STORAGE_ZH_CN
        }

    fun toLocaleListCompat(): LocaleListCompat =
        when (this) {
            SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            EN -> LocaleListCompat.forLanguageTags("en")
            ZH_CN -> LocaleListCompat.forLanguageTags("zh-CN")
        }

    companion object {
        const val STORAGE_SYSTEM = "system"
        const val STORAGE_EN = "en"
        const val STORAGE_ZH_CN = "zh-CN"

        fun fromStorageValue(value: String?): AppLocale =
            when (value) {
                STORAGE_EN -> EN
                STORAGE_ZH_CN -> ZH_CN
                else -> SYSTEM
            }
    }
}

fun AppLocale.applyToApplication() {
    AppCompatDelegate.setApplicationLocales(toLocaleListCompat())
}
