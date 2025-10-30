/* Spring Boot Debezium Oracle CDC prototype build file */

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.integration)
    implementation(libs.spring.boot.starter.kafka)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(libs.spring.integration.core)
    implementation(libs.spring.integration.debezium)

    // Oracle JDBC (ojdbc10)
    implementation(libs.ojdbc10)

    // Debezium embedded engine and Oracle connector
    implementation(libs.debezium.embedded)
    implementation(libs.debezium.connector.oracle)

    // Debezium Kafka storage for offsets and schema history
    implementation(libs.debezium.storage.kafka)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.example.cdc.CdcApplication")
}
