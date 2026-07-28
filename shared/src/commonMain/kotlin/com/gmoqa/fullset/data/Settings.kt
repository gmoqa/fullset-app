package com.gmoqa.fullset.data

import com.russhwolf.settings.Settings

/** Preferencias de cada plataforma (SharedPreferences en Android, NSUserDefaults en iOS). */
expect fun createSettings(): Settings
