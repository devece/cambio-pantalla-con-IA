plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.tablero.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.tablero.launcher"
        minSdk = 21 // las radios chinas de auto suelen traer Android 5 a 10
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Dónde corre /api/license. Cámbialo con -PtableroApiBase=https://tu-dominio.com
        val apiBase = providers.gradleProperty("tableroApiBase").getOrElse("https://TU-DOMINIO.vercel.app")
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))
}

// editions.json vive en public/shared/ (lo comparte el sitio): se copia a los assets al compilar.
val copyEditions = tasks.register<Copy>("copyEditions") {
    from(rootProject.file("../public/shared/editions.json"))
    into(layout.buildDirectory.dir("generated/editions"))
}
android.sourceSets.getByName("main").assets.srcDir(
    files(layout.buildDirectory.dir("generated/editions")).builtBy(copyEditions)
)
