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
        // En un release el workflow las pasa desde la etiqueta (-PtableroVersion=1.2.3 -PtableroVersionCode=10203); a mano, estas.
        versionCode = providers.gradleProperty("tableroVersionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("tableroVersion").getOrElse("0.1.0")

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

    // Lint revisa el código contra la versión mínima de Android (minSdk 21): atrapa llamadas a APIs que no existen en Android 5 a 10 y
    // permisos mal usados. Sus tareas leen los assets que generan copyEditions y copyWeb: abajo se declara esa dependencia (era la
    // causa de que «lintVital» fallara en la versión firmada, y por eso estaba apagado).
    lint {
        abortOnError = false          // por ahora informa; cuando el informe quede limpio se vuelve estricto (docs/CALIDAD.md)
        textReport = true
        textOutput = layout.buildDirectory.file("reports/lint-results.txt").get().asFile
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("tablero")?.let { signingConfig = it }
        }
    }
}

dependencies {
    implementation(project(":core"))

    // Pruebas del módulo de la app. Robolectric simula Android en el PC (en cada versión que se pida) sin necesidad de un emulador.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(kotlin("test"))
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
    // Lint también lee esas carpetas generadas (si no las espera, falla con «implicit dependency»).
    if (name.startsWith("lint") || name.startsWith("generate") && name.endsWith("LintReportModel")) dependsOn(copyEditions, copyWeb)
}
