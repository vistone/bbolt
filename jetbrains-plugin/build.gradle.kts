plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.protonail"
version = "1.0.20"

repositories {
    mavenCentral()
    mavenLocal()
}

// IntelliJ Platform Gradle Plugin configuration
intellij {
    version.set("2023.3")
    type.set("IC")  // IntelliJ IDEA Community
    plugins.set(emptyList())  // No additional plugins required
}

tasks {
    // Disable searching for plugins in Gradle plugin portal
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("261.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        // Allocate more memory for IDE
        jvmArgs = listOf("-Xmx2g")
    }

    // Package the bolt-jna-core classes into the plugin
    prepareSandbox {
        from(jar) {
            into("lib")
        }
    }
}

dependencies {
    // JNA for native library access (5.19.1 required for JDK 25 support)
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    // Include bolt-jna-core from local Maven repository
    implementation("com.protonail.bolt-jna:bolt-jna-core:1.3.1-1")
    implementation("com.protonail.bolt-jna:bolt-jna-native:1.3.1-1")

    // IntelliJ Platform annotations
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
        System.getProperty("bbolt.jammdb.native.path")?.let {
            systemProperty("bbolt.jammdb.native.path", it)
        }
    }
}
