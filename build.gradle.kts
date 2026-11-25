plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.65-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        archiveClassifier.set("")

        relocate(
            "kotlin",
            "com.eastcompany.resourcepackflow.lib.kotlin"
        )
        relocate(
            "kotlinx",
            "com.eastcompany.resourcepackflow.lib.kotlinx"
        )
    }

    jar {
        enabled = false
    }

    val copyJar by registering(Copy::class) {
        dependsOn(shadowJar)
        from(shadowJar.get().archiveFile)
        into("C:/Users/tanak/Minecraft/MC_Server/plugins")
    }

    build {
        dependsOn(copyJar)
    }
}
