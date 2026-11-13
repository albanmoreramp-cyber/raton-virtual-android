plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.albanmorera.ratonvirtual"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.albanmorera.ratonvirtual"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // El modelo de MediaPipe tiene que ir SIN comprimir dentro del APK
    androidResources {
        noCompress += listOf("task", "tflite")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.21")
}

// --- Descarga automática del modelo de la mano (la primera vez que compilas) ---
// Si prefieres, copia tu 'hand_landmarker.task' (el mismo del ordenador) en
// app/src/main/assets/ y esta tarea no descargará nada.
val descargarModelo by tasks.registering {
    val destino = file("src/main/assets/hand_landmarker.task")
    outputs.file(destino)
    doLast {
        if (!destino.exists() || destino.length() < 1_000_000) {
            destino.parentFile.mkdirs()
            val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/" +
                "hand_landmarker/float16/1/hand_landmarker.task"
            println("Descargando el modelo de la mano...")
            uri(url).toURL().openStream().use { input ->
                destino.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(descargarModelo) }
