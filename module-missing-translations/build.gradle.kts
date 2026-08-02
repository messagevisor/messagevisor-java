plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-module-missing-translations")
}

dependencies {
    api(project(":sdk"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}
