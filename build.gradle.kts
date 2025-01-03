import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.4.21"

    java
    application
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
}

repositories {
    mavenCentral()
}

val junitVersion: String by project

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
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("com.zaxxer:HikariCP:6.2.1")
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
    mainClassName = "io.luna.Luna"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    main {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("plugins")
        }
    }
}

tasks.withType<JavaCompile> {

    options.compilerArgs = MutableList(1) { "-Xlint:unchecked" }
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.jvmTarget = "11"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
