// Configuración de plugins a nivel de proyecto (no se aplican aquí, solo se declaran).
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}
