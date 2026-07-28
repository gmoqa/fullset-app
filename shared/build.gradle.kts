// Módulo multiplataforma: dominio y lógica portable, compartida entre Android e iOS.
// La UI y las fronteras de plataforma se irán migrando acá con expect/actual.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

sqldelight {
    databases {
        create("FullsetDatabase") {
            packageName.set("com.gmoqa.diariogamer.db")
        }
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    // Targets iOS (device + simulador). Solo compilan en macOS; en Linux se declaran y el build de
    // Android igual funciona.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:3.0.3")
            // Fechas/horas multiplataforma (formato de las notas del diario, en Format.kt).
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            // Compose Multiplatform: la UI compartida vive en commonMain. En Android estos artefactos
            // se resuelven a Jetpack Compose; en iOS al runtime nativo de Compose.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // `api`: la app (que aún tiene DiaryRepository) ve los tipos SqlDriver/Settings/FullsetDatabase.
            api("app.cash.sqldelight:runtime:2.0.2")
            api("com.russhwolf:multiplatform-settings:1.2.0")
        }
        // Fronteras por plataforma (expect/actual): engine HTTP, driver SQLDelight, settings.
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            // Para el actual de SystemBarsEffect (WindowCompat: color e íconos del status bar).
            implementation("androidx.core:core-ktx:1.13.1")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
    }
}

android {
    namespace = "com.gmoqa.diariogamer.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
