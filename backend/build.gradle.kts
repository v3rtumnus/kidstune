plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kidstune"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web & reactive
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Web dashboard (Thymeleaf reactive + HTMX + Bootstrap 5)
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:4.0.1")
    implementation("org.webjars:bootstrap:5.3.3")
    implementation("org.webjars.npm:htmx.org:2.0.4")
    implementation("org.webjars:webjars-locator-lite:1.0.1")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Caching (Caffeine)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Email
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Web Push (VAPID)
    implementation("nl.martijndwars:web-push:5.1.0")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.0"))
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.projectreactor:reactor-test")

    // E2E browser tests (Playwright Java)
    testImplementation("com.microsoft.playwright:playwright:1.46.0")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "kidstune.jar"
}

tasks.withType<Test> {
    useJUnitPlatform()
    // On Windows dev machines, point Testcontainers at the Docker Desktop Linux engine pipe.
    // On Linux CI the default Unix socket (/var/run/docker.sock) is used automatically.
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        jvmArgs("-Dtc.host=npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}

// Exclude E2E tests from the default `test` task to keep CI unit/integration runs fast.
tasks.test {
    useJUnitPlatform {
        excludeTags("e2e")
    }
}

// Dedicated task: ./gradlew e2eTest
tasks.register<Test>("e2eTest") {
    description = "Run Playwright E2E browser tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("e2e") }
    mustRunAfter(tasks.test)
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        jvmArgs("-Dtc.host=npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}

// Download Chromium binaries: ./gradlew playwrightInstall
// Browsers are cached in ~/.cache/ms-playwright so only needs to run once per machine/agent.
// Does NOT install OS-level system deps — run playwrightInstallDeps once as root for that.
tasks.register<JavaExec>("playwrightInstall") {
    description = "Download Playwright Chromium binaries (no OS deps)"
    group = "build setup"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = listOf("install", "chromium")
}

// Install OS-level browser dependencies (requires root / sudo).
// Run once on a new CI agent: sudo ./gradlew playwrightInstallDeps
tasks.register<JavaExec>("playwrightInstallDeps") {
    description = "Install OS-level Chromium system dependencies (must run as root)"
    group = "build setup"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = listOf("install-deps", "chromium")
}
