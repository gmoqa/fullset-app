import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

// La API key de SteamGridDB (carátulas de plataformas modernas) vive en local.properties,
// fuera del control de versiones. Sin ella, el buscador de carátulas queda inactivo.
val steamGridDbKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("STEAMGRIDDB_API_KEY", "")

sqldelight {
    databases {
        create("FullsetDatabase") {
            packageName.set("com.gmoqa.diariogamer.db")
        }
    }
}

android {
    namespace = "com.gmoqa.diariogamer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gmoqa.diariogamer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // whisper.cpp (transcripción local). Solo arm64-v8a: cubre los dispositivos reales y
        // evita multiplicar el peso del .so por ABIs que no vamos a usar.
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "STEAMGRIDDB_API_KEY", "\"$steamGridDbKey\"")
    }

    // Compila whisper.cpp + ggml (backend CPU) como libwhisper_jni.so.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // para BuildConfig.DEBUG: el flag de "empty state" solo se ve en debug
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // ViewModel en Compose + collectAsStateWithLifecycle (estado reactivo consciente del ciclo de vida).
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Carga de imágenes (Coil 3, multiplataforma) desde archivos locales y URLs remotas.
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Corrutinas para resolver/descargar carátulas fuera del hilo principal.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Multiplataforma (KMP-ready): JSON, fechas y preferencias sin APIs Android/JVM.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.russhwolf:multiplatform-settings:1.2.0")

    // HTTP multiplataforma (KMP-ready). En iOS el engine sería ktor-client-darwin.
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // Base de datos multiplataforma (KMP-ready). En iOS el driver sería native-driver.
    implementation("app.cash.sqldelight:android-driver:2.0.2")
    // Lecturas reactivas: Query.asFlow() + mapToList/mapToOneOrNull (re-emiten al cambiar la tabla).
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
