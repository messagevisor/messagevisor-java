import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

plugins {
    `java-library`
}

allprojects {
    group = "com.messagevisor"
    version = providers.gradleProperty("version").get()
}

subprojects {
    apply(plugin = "java-library")

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Jar>().configureEach {
        manifest.attributes["Implementation-Version"] = project.version
    }

    tasks.withType<Javadoc>().configureEach {
        // Keep doclint checking malformed public documentation while avoiding hundreds of
        // low-value warnings for self-describing bean accessors and record components.
        (options as StandardJavadocDocletOptions)
            .addStringOption("Xdoclint:all,-missing", "-quiet")
    }

    if (name in setOf("sdk", "module-icu", "module-interpolation", "module-missing-translations")) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        val publishedArtifactId = when (name) {
            "sdk" -> "messagevisor-sdk"
            else -> "messagevisor-$name"
        }
        val publishedName = when (name) {
            "sdk" -> "Messagevisor Java SDK"
            "module-icu" -> "Messagevisor ICU module"
            "module-interpolation" -> "Messagevisor interpolation module"
            else -> "Messagevisor missing-translations module"
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = publishedArtifactId
                    pom {
                        name.set(publishedName)
                        description.set("Messagevisor runtime components for Java")
                        url.set("https://messagevisor.com")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }
                        developers {
                            developer {
                                id.set("fahad19")
                                name.set("Fahad Heylaal")
                                url.set("https://fahad19.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/messagevisor/messagevisor-java.git")
                            developerConnection.set("scm:git:ssh://git@github.com/messagevisor/messagevisor-java.git")
                            url.set("https://github.com/messagevisor/messagevisor-java")
                        }
                    }
                }
            }

            val repositoryUrl = providers.environmentVariable("MAVEN_REPOSITORY_URL")
            if (repositoryUrl.isPresent) {
                repositories {
                    maven {
                        name = "release"
                        url = uri(repositoryUrl.get())
                        val repositoryUsername = providers.environmentVariable("MAVEN_REPOSITORY_USERNAME")
                        if (repositoryUsername.isPresent) {
                            credentials {
                                username = repositoryUsername.get()
                                password = providers.environmentVariable("MAVEN_REPOSITORY_PASSWORD").orNull
                            }
                        }
                    }
                }
            }
        }

        configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY")
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
            if (signingKey.isPresent) {
                useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
                sign(project.extensions.getByType<PublishingExtension>().publications["mavenJava"])
            }
        }

        tasks.withType<Sign>().configureEach {
            onlyIf { providers.environmentVariable("SIGNING_KEY").isPresent }
        }
    }
}
