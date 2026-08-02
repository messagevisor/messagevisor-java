plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-module-icu")
}

dependencies {
    api(project(":sdk"))
    api(libs.icu4j)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
