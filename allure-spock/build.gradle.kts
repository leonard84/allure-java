description = "Allure Spock Framework Integration"

plugins {
    groovy
    `jvm-test-suite`
}

val spock1FrameworkVersion = "1.3-groovy-2.5"
val spock2FrameworkVersion = "2.1-groovy-3.0"

testing {
    suites {
        all {
            dependencies {
                api("org.assertj:assertj-core")
                api("org.mockito:mockito-core")
                api("org.slf4j:slf4j-simple")
                api(project(":allure-java-commons-test"))
                api(project(":allure-junit-platform"))
            }
        }
        val spock1Test by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project)
                implementation("org.spockframework:spock-core:$spock1FrameworkVersion")
            }
            useJUnitJupiter()
            sources {
                groovy {
                    srcDirs(listOf("src/testFixtures/groovy"))
                }
            }
        }
        val spock2Test by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project)
                implementation("org.junit.platform:junit-platform-launcher")
                implementation("org.spockframework:spock-core:$spock2FrameworkVersion")
            }
            useJUnitJupiter()
            sources {
                groovy {
                    srcDirs(listOf("src/testFixtures/groovy"))
                }
            }

            targets {
                all {
                    testTask.configure {
                        useJUnitPlatform {
                            // we don't want to execute spock directly
                            includeEngines("junit-jupiter")
                        }
                    }
                }
            }
        }
    }
}


dependencies {
    api(project(":allure-java-commons"))
    compileOnly("org.spockframework:spock-core:$spock1FrameworkVersion")
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Automatic-Module-Name" to "io.qameta.allure.spock"
            )
        )
    }
}

tasks.test {
    dependsOn("spock1Test", "spock2Test")
}
