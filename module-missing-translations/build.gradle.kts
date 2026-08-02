plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-module-missing-translations")
}

dependencies {
    api(project(":sdk"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
