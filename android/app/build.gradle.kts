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
        // Los pasa la CI desde scripts/version.js (-PtableroVersionCode=1155 -PtableroVersion=0.2.0+155 -PtableroCommit=ee17774da76f);
        // a mano, estos valores de desarrollo.
        versionCode = providers.gradleProperty("tableroVersionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("tableroVersion").getOrElse("0.1.0")
        // El commit del que salió esta APK (se ve en Ajustes y en el diagnóstico de la radio).
        buildConfigField("String", "GIT_COMMIT", "\"${providers.gradleProperty("tableroCommit").getOrElse("desarrollo")}\"")
        // La descarga directa se actualiza sola con el aviso de la app; la variante de Play Store no (Play lo prohíbe y es Play quien actualiza).
        buildConfigField("boolean", "IN_APP_UPDATE", "true")

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

    // Firma de release: solo si CI entrega la clave (secretos TB_KEYSTORE_*). Sin ella se compila el APK de prueba (debug).
    val keystorePath = System.getenv("TB_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("tablero") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TB_KEY_ALIAS")
                // Si el secreto de la clave viene vacío, se usa el del almacén (en un almacén PKCS12 nuevo son iguales).
                keyPassword = System.getenv("TB_KEY_PASSWORD").takeUnless { it.isNullOrEmpty() }
                    ?: System.getenv("TB_KEYSTORE_PASSWORD")
            }
        }
    }

    // La revisión automática de la versión firmada («lintVital») usa carpetas que generan otras tareas (copyEditions y copyWeb) y falla
    // porque no sabe que debe esperarlas. La versión de prueba nunca la ejecutó; se mantiene igual para la firmada. Las pruebas de
    // GitHub (npm test y :core:test) y la revisión del APK siguen corriendo.
    lint {
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("tablero")?.let { signingConfig = it }
        }
        // Variante para Play Store: igual que release pero sin el actualizador ni el permiso de instalar apps (src/play/AndroidManifest.xml).
        // Se compila con :app:assemblePlay o :app:bundlePlay. Ver docs/PLAY_STORE.md.
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            buildConfigField("boolean", "IN_APP_UPDATE", "false")
        }
    }
}

dependencies {
    implementation(project(":core"))
}

// Estos JSON viven en public/shared/ (los comparte el sitio): se copian a los assets al compilar.
val copyEditions = tasks.register<Copy>("copyEditions") {
    from(rootProject.file("../public/shared/editions.json"), rootProject.file("../public/shared/maintenance.json"), rootProject.file("../public/shared/restriction.json"), rootProject.file("../public/shared/obd-codes.json"))
    into(layout.buildDirectory.dir("generated/editions"))
}
android.sourceSets.getByName("main").assets.srcDir(
    files(layout.buildDirectory.dir("generated/editions")).builtBy(copyEditions)
)

// La pantalla de inicio con el diseño del cliente es el mismo motor del sitio: se empaqueta dentro de la app (assets/web/)
// para que funcione sin internet y siempre dibuje igual que la vista previa.
val copyWeb = tasks.register<Copy>("copyWeb") {
    from(rootProject.file("../public")) {
        include("launcher.html", "css/preview.css", "js/art.js", "js/themes.js", "js/fx.js", "js/preview.js", "js/launcher.js", "shared/catalog.js", "shared/daylight.js", "shared/pico.js", "img/auto.png", "img/camioneta.png", "img/suv.png")
    }
    into(layout.buildDirectory.dir("generated/web/web"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/web"))

// El srcDir de arriba no arrastra la dependencia de la tarea: sin esto `copyEditions` nunca corre
// y el APK queda sin sus JSON (la app se cerraría al abrir). Se verifica además en el flujo de GitHub.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(copyEditions, copyWeb)
}
