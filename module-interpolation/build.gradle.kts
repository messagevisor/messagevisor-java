plugins {
    `java-library`
}

base {
    archivesName.set("messagevisor-module-interpolation")
}

dependencies {
    api(project(":sdk"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}
