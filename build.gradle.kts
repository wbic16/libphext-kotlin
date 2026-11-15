plugins {
    kotlin("jvm") version "2.0.21"
}

group = "io.phext"
version = "0.6.0"

repositories {
    mavenCentral()
}

dependencies {
    // Pure-Java XZ (the xz seal): standard xz streams, preset 6 + CRC64.
    implementation("org.tukaani:xz:1.10")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    // the shared chain fixtures live at tests/fixtures/chains (the family
    // layout, as in libphext-rs / libphext-cs / libphext-cpp); the Gradle
    // test working directory is the project directory, so tests read them
    // from there directly.
    testLogging {
        events("passed", "failed", "skipped")
    }
}
