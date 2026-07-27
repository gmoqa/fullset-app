package com.gmoqa.diariogamer.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

actual fun createSettings(): Settings = SharedPreferencesSettings(
    AndroidApp.context.getSharedPreferences("diario_gamer_prefs", Context.MODE_PRIVATE)
)
