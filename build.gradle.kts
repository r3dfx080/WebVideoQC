import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.foxycorp"
version = "0.4"
description = "WebVideoQC"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.projectlombok:lombok")
    //developmentOnly("org.springframework.boot:spring-boot-devtools")
    //developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Copy>("copyApplicationProperties") {
    from("src/main/resources/application.properties")
    into(layout.buildDirectory.dir("libs"))
}
tasks.register<Copy>("copyWindowsStartScript") {
    from("src/main/resources/start.bat")
    into(layout.buildDirectory.dir("libs"))
}

tasks.named<BootJar>("bootJar"){
    archiveFileName.set("WebVideoQC-${project.version}.jar")
}

tasks.register<Zip>("windowsDist") {
    dependsOn("bootJar", "copyApplicationProperties", "copyWindowsStartScript")
    archiveBaseName.set("WebVideoQC")
    archiveVersion.set(version.toString())
    archiveClassifier.set("windows")

    from(layout.buildDirectory.dir("libs")) {
        include("WebVideoQC-${project.version}.jar", "application.properties", "start.bat")
    }
}
