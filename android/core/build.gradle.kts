// Reglas del launcher sin dependencia de Android: qué incluye cada edición, formato del código
// de licencia e interpretación de la respuesta del servidor. Así se pueden probar en cualquier PC.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // En Android, org.json ya viene en el sistema: solo se necesita para compilar y probar en el PC.
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Las pruebas leen el MISMO editions.json que usan el sitio y la app.
    systemProperty("editions.path", rootProject.file("../public/shared/editions.json").absolutePath)
    systemProperty("maintenance.path", rootProject.file("../public/shared/maintenance.json").absolutePath)
    systemProperty("restriction.path", rootProject.file("../public/shared/restriction.json").absolutePath)
    systemProperty("obd.codes.path", rootProject.file("../public/shared/obd-codes.json").absolutePath)
    // Para revisar los autos a ojo: ./gradlew :core:test -Dcar3d.out=/carpeta
    System.getProperty("car3d.out")?.let { systemProperty("car3d.out", it) }
}
