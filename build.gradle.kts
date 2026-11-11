import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec

plugins {
    val kotlinVersion = "1.9.25"

    java
    application
    idea
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    implementation("com.lmax:disruptor:3.4.2")
    // Includes the HTTP/WebSocket parser and resource-limit fixes used by the optional browser listener.
    implementation("io.netty:netty-all:4.1.140.Final")
    implementation("com.google.guava:guava:33.4.8-jre")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.github.classgraph:classgraph:4.8.179")
    implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.9.25") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

group = "luna"
version = "1.0"

application {
    mainClass = "io.luna.Luna"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    sourceSets {
        main {
            java.srcDirs("src/main/java")
        }
        test {
            java.srcDirs("src/test/java")
        }
    }
}

kotlin {
    sourceSets {
        main {
            kotlin.setSrcDirs(emptyList<Any>())
            kotlin.srcDirs(
                "src/main/java",
                "src/main/kotlin/api",
                "src/main/kotlin/engine",
                "src/main/kotlin/game"
            )
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs = MutableList(1) { "-Xlint:unchecked" }
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().all {
    // Allow game scripts (.kts) to compile from the configured source directories.
    kotlinOptions.freeCompilerArgs = MutableList(1) { "-Xallow-any-scripts-in-source-roots" }
    kotlinOptions.jvmTarget = "21"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Decoding the cache's map data, which some tests do, needs more than the default heap.
    maxHeapSize = "2g"
}

// Generates a separate pair for installation on the server and client. Override the destination with
// -PrsaOutputDir=path; existing keys are never replaced. This task does not compile or start Luna.
tasks.register("rsakeygen") {
    group = "luna"
    description = "Generates rsapriv.json and rsapub.json in build/rsakeygen (or -PrsaOutputDir=path)."
    doNotTrackState("RSA keys must be freshly generated and must never enter the build cache.")
    val outputDirectory = providers.gradleProperty("rsaOutputDir").map { file(it) }
        .getOrElse(layout.buildDirectory.dir("rsakeygen").get().asFile).toPath()

    doLast {
        val privateFile = outputDirectory.resolve("rsapriv.json")
        val publicFile = outputDirectory.resolve("rsapub.json")
        if (listOf(privateFile, publicFile).any { Files.exists(it, LinkOption.NOFOLLOW_LINKS) }) {
            throw GradleException("RSA keys already exist in $outputDirectory. Choose a new -PrsaOutputDir=path.")
        }
        Files.createDirectories(outputDirectory)

        // Retain the old generator's 1024-bit size for the revision 377 login packet.
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4))
        val pair = generator.generateKeyPair()
        val privateKey = pair.private as RSAPrivateKey
        val publicKey = pair.public as RSAPublicKey
        fun keyJson(exponent: BigInteger) = """
            {
              "modulus": "${publicKey.modulus}",
              "exponent": "$exponent"
            }
        """.trimIndent() + "\n"

        val privateAttributes = if (Files.getFileStore(outputDirectory).supportsFileAttributeView("posix")) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            emptyArray()
        }
        Files.createFile(privateFile, *privateAttributes)
        try {
            Files.writeString(privateFile, keyJson(privateKey.privateExponent))
            Files.writeString(publicFile, keyJson(publicKey.publicExponent), StandardOpenOption.CREATE_NEW)
        } catch (failure: Exception) {
            Files.deleteIfExists(privateFile)
            throw failure
        }
        logger.lifecycle("Generated RSA keys in {}", outputDirectory)
        logger.lifecycle("Install rsapriv.json in the server's data/net/rsa directory; use rsapub.json in the client.")
        logger.lifecycle("Keep the private key out of source control and client distributions.")
    }
}

// Draws the bot web-walker's data (everything in data/game/bots/webwalk) over the terrain of the cache and writes it to a
// PNG, to get a quick idea of what the web looks like. Properties, all optional: -Pplane=0, -Pscale=3 (pixels per tile),
// -Pbounds=minX,minY,maxX,maxY (or "auto" to fit the nodes of the plane; the default is the surface of the world) and
// -Pout=build/webwalk/webwalk-map.png. The scale only sizes the terrain, so a small area at a large scale (for instance
// -Pbounds=3190,3180,3260,3250 -Pscale=15) shows the same nodes and edges, just with more room around them.
tasks.register<JavaExec>("renderWebWalkMap") {
    group = "luna"
    description = "Draws the web-walker graph over the world map as a PNG."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "api.bot.webwalk.visualize.WebWalkMapRenderer"
    systemProperty("java.awt.headless", "true")
    for ((property, name) in listOf("plane" to "plane", "scale" to "scale", "bounds" to "bounds", "out" to "out")) {
        project.findProperty(property)?.let { systemProperty("luna.webwalk.map.$name", it.toString()) }
    }
    maxHeapSize = "4g"
}
