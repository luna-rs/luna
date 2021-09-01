
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.3.60"
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
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.apache.logging.log4j:log4j-core:2.14.0")
    implementation("org.apache.logging.log4j:log4j-api:2.14.0")
    implementation("org.slf4j:slf4j-nop:1.7.30")
    implementation("com.lmax:disruptor:3.4.2")
    implementation("io.netty:netty-all:4.1.56.Final")
    implementation("com.google.guava:guava:30.1-jre")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("org.mindrot:jbcrypt:0.4-atlassian-1")
    implementation("io.github.classgraph:classgraph:4.8.59")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation("org.openjfx:javafx-controls:11.0.1")
    implementation("org.openjfx:javafx-fxml:11.0.1")
    implementation("org.openjfx:javafx-swing:11.0.1")
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.mockito:mockito-core:3.12.4")
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
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("plugins")
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
