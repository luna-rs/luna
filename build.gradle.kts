import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.collections.MutableList

plugins {
    val kotlinVersion = "1.3.11"
    val jfxVersion = "0.0.8"

    java
    kotlin("jvm") version kotlinVersion apply false
    application
    id("org.openjfx.javafxplugin") version jfxVersion
}

repositories {
    mavenLocal()
    maven {
        url = uri("http://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    implementation("org.apache.logging.log4j:log4j-api:2.11.1")
    implementation("org.slf4j:slf4j-nop:1.7.25")
    implementation("com.lmax:disruptor:3.4.2")
    implementation("io.netty:netty-all:4.1.32.Final")
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.11")

    implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.3.11")
    implementation("org.jetbrains.kotlin:kotlin-script-util:1.3.11")
    implementation("org.jetbrains.kotlin:kotlin-compiler:1.3.11")
    implementation("org.openjfx:javafx-controls:11.0.1")
    implementation("org.openjfx:javafx-fxml:11.0.1")
    implementation("org.openjfx:javafx-swing:11.0.1")
    implementation("com.zaxxer:HikariCP:3.3.0")
    implementation("org.mockito:mockito-core:2.24.5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:+")
    testImplementation("org.junit.jupiter:junit-jupiter-params:+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:+")

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

sourceSets["main"].withConvention(KotlinSourceSet::class) {
    kotlin.srcDir("plugins")
}

javafx {
    version = "11"
    modules("javafx.controls", "javafx.fxml", "javafx.swing")
}

tasks.withType<JavaCompile>{
    options.compilerArgs = MutableList(1) {"-Xlint:unchecked"}
    options.encoding= "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<Test>("test") {
        useJUnitPlatform()
}
