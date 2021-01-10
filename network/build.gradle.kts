plugins {
    java
}

group = "luna"
version = "1.0"

repositories {
    jcenter()
}

val junitVersion: String by project

dependencies {
    implementation("io.netty:netty-all:4.1.56.Final")
    implementation("com.google.guava:guava:30.1-jre")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}
