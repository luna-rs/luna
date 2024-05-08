
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.9.23"
    val jfxVersion = "0.0.8"

    java
    application
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.openjfx.javafxplugin") version jfxVersion
}

repositories {
    jcenter()
}

val junitVersion: String by project

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.slf4j:slf4j-nop:2.0.12")
    implementation("com.lmax:disruptor:3.4.2")
    implementation("io.netty:netty-all:4.1.107.Final")
    implementation("com.google.guava:guava:33.2.0-jre")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("org.mindrot:jbcrypt:0.4-atlassian-1")
    implementation("io.github.classgraph:classgraph:4.8.168")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation("org.openjfx:javafx-controls:21.0.2")
    implementation("org.openjfx:javafx-fxml:11.0.1")
    implementation("org.openjfx:javafx-swing:21.0.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
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
        java {
            srcDir("src/main/java")
        }
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("plugins")
        }
    }
    test {
        java {
            srcDir("src/test/java")
        }
    }
}

javafx {
    version = "11"
    modules("javafx.controls", "javafx.fxml", "javafx.swing")
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
