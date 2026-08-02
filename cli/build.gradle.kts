plugins {
    application
}

base {
    archivesName.set("messagevisor-cli")
}

application {
    mainClass.set("com.messagevisor.cli.MessagevisorJavaCli")
    applicationDefaultJvmArgs = listOf("-Dmessagevisor.version=${project.version}")
}

dependencies {
    implementation(project(":sdk"))
    implementation(project(":module-interpolation"))
    implementation(project(":module-icu"))
    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
