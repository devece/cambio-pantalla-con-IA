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

// Estos JSON viven en public/shared/ (los comparte el sitio): se copian a los assets al compilar.
val copyEditions = tasks.register<Copy>("copyEditions") {
    from(rootProject.file("../public/shared/editions.json"), rootProject.file("../public/shared/maintenance.json"), rootProject.file("../public/shared/restriction.json"))
    into(layout.buildDirectory.dir("generated/editions"))
}
android.sourceSets.getByName("main").assets.srcDir(
    files(layout.buildDirectory.dir("generated/editions")).builtBy(copyEditions)
)

// El srcDir de arriba no arrastra la dependencia de la tarea: sin esto `copyEditions` nunca corre
// y el APK queda sin sus JSON (la app se cerraría al abrir). Se verifica además en el flujo de GitHub.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(copyEditions)
}
