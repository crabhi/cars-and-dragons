plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(27))
    // Kotlin 2.4 emits Java 26 bytecode at most; keep javac on the same target.
    targetCompatibility = JavaVersion.VERSION_26
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26)
    }
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("cz.crabhi.dragons.cli.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}
