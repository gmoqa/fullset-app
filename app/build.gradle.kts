import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// La API key de SteamGridDB (carátulas de plataformas modernas) vive en local.properties,
// fuera del control de versiones. Sin ella, el buscador de carátulas queda inactivo.
//
// `-PsteamGridDbKey=` la deja vacía a propósito. Hace falta para los APK que se comparten: la clave
// se hornea en BuildConfig, o sea que termina en el dex y **cualquiera que descomprima el APK puede
// leerla**. En una build para uno mismo da igual; en una que se reparte, es regalar la credencial.
val steamGridDbKey: String = (findProperty("steamGridDbKey") as String?)
    ?: Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("STEAMGRIDDB_API_KEY", "")

// El esquema SQLDelight y el driver viven en :shared (multiplataforma). Acá solo se consumen.

android {
    namespace = "com.gmoqa.fullset"
    compileSdk = 35

    // Los catálogos, el registro de plataformas y el seed viven en `data/` en la raíz, no dentro de
    // este módulo: los consumen Android **e iOS**, y tenerlos acá obligaba al proyecto de Xcode a
    // cruzarse hasta `../app/src/main/assets/`, o sea el build de una plataforma metiendo la mano en
    // el árbol de fuentes de la otra. La estructura interna (catalogs/, config/, seed/) se conserva
    // tal cual porque es la ruta que pide `readTextAsset`.
    sourceSets["main"].assets.srcDirs("../data")

    defaultConfig {
        applicationId = "com.gmoqa.fullset"
        minSdk = 26
        targetSdk = 35
        // `versionCode` tiene que subir en cada APK que se reparta: Android rechaza instalar encima
        // uno con un número igual o menor, y como estos van a mano no hay quien lo lleve por vos.
        versionCode = 3
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

    // Firma de release. Si existe `keystore.properties` (fuera del repo) se usa esa; si no, se cae
    // al keystore de debug para poder generar un APK **instalable** y pasárselo a alguien a mano.
    // Sirve para compartir, NO para publicar: la Play Store exige una firma propia, y una app
    // firmada con la clave de debug no se puede actualizar después con otra distinta.
    signingConfigs {
        create("sideload") {
            val props = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val store = props.getProperty("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                val debugStore = listOf(
                    File(System.getProperty("user.home"), ".android/debug.keystore"),
                    File(System.getProperty("user.home"), ".config/.android/debug.keystore"),
                ).firstOrNull { it.exists() }
                if (debugStore != null) {
                    storeFile = debugStore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
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
    // Módulo multiplataforma: dominio/lógica portable compartida con iOS.
    implementation(project(":shared"))

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
    // HTTP para WhisperModelStore (descarga del modelo). SteamGridDb/CoverArt ya usan el de :shared.
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // Lecturas reactivas de SQLDelight: Query.asFlow() + mapToList/mapToOneOrNull. La BD y el driver
    // vienen de :shared; settings y android-driver también.
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
