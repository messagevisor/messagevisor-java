plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-sdk")
}

dependencies {
    api(libs.jackson.databind)
    api(libs.icu4j)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
