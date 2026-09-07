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
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

sourceSets.test {
    resources.srcDir("../sdk/src/test/resources")
}
