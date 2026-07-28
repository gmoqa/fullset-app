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
    // Silencia el warning "expect/actual classes are in Beta": usamos expect class/object a propósito
    // en las fronteras de plataforma (PlatformImage, FileStore).
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
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
            // Íconos de Material usados por la UI (SportsEsports, Bookmarks, Info, flechas…).
            implementation(compose.materialIconsExtended)
            // Recursos multiplataforma (drawables): genera la clase `Res` y `painterResource`.
            // `api`: :app usa la `Res` generada y `painterResource` (necesita el runtime en su classpath).
            api(compose.components.resources)
            // Carga de imágenes multiplataforma (carátulas): Coil 3, mismo que ya usa :app.
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            // `api`: la app ve los tipos SqlDriver/Settings/FullsetDatabase y los Flow del repo.
            api("app.cash.sqldelight:runtime:2.0.2")
            api("com.russhwolf:multiplatform-settings:1.2.0")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            // Lecturas reactivas de SQLDelight (asFlow/mapToList) usadas por DiaryRepository.
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
            // ViewModel + viewModelScope multiplataforma (DiaryViewModel común). `api`: :app usa el
            // tipo con viewModel().
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
            // collectAsStateWithLifecycle en las pantallas comunes (GameDetailScreen).
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
        }
        // Fronteras por plataforma (expect/actual): engine HTTP, driver SQLDelight, settings.
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            // Para el actual de SystemBarsEffect (WindowCompat: color e íconos del status bar).
            implementation("androidx.core:core-ktx:1.13.1")
            // Para el actual de BackHandler (botón de retroceso del sistema en Android).
            implementation("androidx.activity:activity-compose:1.9.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
    }
}

// Recursos de Compose Multiplatform: la clase generada `Res` (drawables) queda pública y en un
// paquete estable para que :app también la consuma.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.gmoqa.diariogamer.resources"
    generateResClass = always
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
