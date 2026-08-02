plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-module-interpolation")
}

dependencies {
    api(project(":sdk"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
