import java.util.Properties

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
            packageName.set("com.gmoqa.fullset.db")
        }
    }
}

// Clave de SteamGridDB para iOS: se genera un Kotlin desde `local.properties` —la misma fuente que
// usa Android vía BuildConfig—, así hay una sola fuente para las dos plataformas. El archivo generado
// vive en build/ (gitignored): la clave nunca se versiona. Vacía → el buscador de carátulas queda off.
val steamGridApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("STEAMGRIDDB_API_KEY", "")

val steamGridGenDir = layout.buildDirectory.dir("generated/steamgrid/kotlin")
val generateSteamGridConfig = tasks.register("generateSteamGridConfig") {
    outputs.dir(steamGridGenDir)
    inputs.property("key", steamGridApiKey) // regenera si cambia la clave
    doLast {
        val escaped = steamGridApiKey.replace("\\", "\\\\").replace("\"", "\\\"")
        val file = steamGridGenDir.get().file("com/gmoqa/fullset/SteamGridConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.gmoqa.fullset\n\ninternal const val STEAMGRIDDB_API_KEY: String = \"$escaped\"\n",
        )
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
    // Targets iOS (device + simulador). Cada uno exporta un framework estático `Shared` que el
    // proyecto Xcode (iosApp) linkea; `MainViewController` es el entry point de la UI compartida.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        // cinterop con whisper.cpp (transcripción de notas de voz). El .a se linkea en iosApp.
        target.compilations.getByName("main").cinterops.create("whispercpp")
    }

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
            // viewModel { } multiplataforma para crear el DiaryViewModel en MainViewController.
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
            // Fetcher de red de Coil (vía Ktor/Darwin) para cargar carátulas remotas en iOS.
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
        }
        // Tests de la lógica pura (Format, Condition, GameSearch, Platform): corren en JVM con
        // `./gradlew :shared:testDebugUnitTest` (y en iOS si algún día corremos esos targets).
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Tests que necesitan una BD real (SyncSnapshot sobre DiaryRepository): SQLite JDBC en
        // memoria + Settings en memoria. Solo JVM; no toca Android (no requiere Robolectric).
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
                implementation("com.russhwolf:multiplatform-settings-test:1.2.0")
            }
        }
    }
}

// El Kotlin generado con la clave de SteamGridDB entra al source set de iOS; los compiladores de iOS
// dependen del task que lo genera.
kotlin.sourceSets.getByName("iosMain").kotlin.srcDir(steamGridGenDir)
tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
    dependsOn(generateSteamGridConfig)
}

// Recursos de Compose Multiplatform: la clase generada `Res` (drawables) queda pública y en un
// paquete estable para que :app también la consuma.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.gmoqa.fullset.resources"
    generateResClass = always
}

android {
    namespace = "com.gmoqa.fullset.shared"
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
