import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.1.20"

    java
    application
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    implementation("com.lmax:disruptor:3.4.2")
    implementation("io.netty:netty-all:4.1.107.Final")
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.github.classgraph:classgraph:4.8.179")
    implementation(kotlin("stdlib"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mockito:mockito-core:5.14.2")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

group = "luna"
version = "1.0"

application {
    mainClass.set("io.luna.Luna")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs = MutableList(1) { "-Xlint:unchecked" }
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)

        // Add Kotlin scripting compiler options
        freeCompilerArgs.set(listOf(
            "-Xallow-any-scripts-in-source-roots",
            "-Xuse-fir-lt=false"
        ))
    }
}

tasks.test {
    useJUnitPlatform()
}
