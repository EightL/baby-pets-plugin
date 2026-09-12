plugins {
    `java-library`
}

group = "com.petsplugin"
version = "1.3.0"

// One JAR for Java 21 and newer servers. Newer optional features are resolved at runtime.
// Compile against the oldest supported API; Paper remaps its legacy attribute/enum references.
// Legacy deployment scripts may still pass targetMinecraftVersion; it does not change the build.

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }
    withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.release.set(21)
    }
}
