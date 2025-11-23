import org.jooq.meta.jaxb.Logging.*

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
  id("org.jooq.jooq-codegen-gradle") version "3.19.26"
}

group = "com.functionaldude"
version = "0.0.1-SNAPSHOT"
description = "API for a custom GPT & Paperless"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.0.0-RC1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
  implementation("org.postgresql:postgresql:42.7.3")
  implementation("org.flywaydb:flyway-database-postgresql")
  jooqCodegen("org.postgresql:postgresql:42.7.3")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

sourceSets {
  named("main") {
    java {
      srcDir("$buildDir/generated-sources/jooq")
    }
  }
}

// Very small .env parser for Gradle
val dotenv: Map<String, String> = run {
  val envFile = file(".env")
  if (envFile.exists()) {
    envFile.readLines()
      .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          null
        } else {
          val parts = trimmed.split("=", limit = 2)
          if (parts.size == 2) parts[0] to parts[1] else null
        }
      }
      .toMap()
  } else {
    emptyMap()
  }
}

fun env(key: String, default: String? = null): String {
  return System.getenv(key) // real env var (CI, shell, etc.)
    ?: dotenv[key] // fallback to .env
    ?: default
    ?: error("Missing env var '$key'")
}

jooq {
  configuration {
    logging = WARN

    jdbc {
      driver = "org.postgresql.Driver"
      url = env("PAPERLESS_DB_URL")
      user = env("PAPERLESS_DB_USER")
      password = env("PAPERLESS_DB_PASSWORD")
    }

    generator {
      name = "org.jooq.codegen.KotlinGenerator"

      database {
        // Postgres database introspection
        name = "org.jooq.meta.postgres.PostgresDatabase"
        inputSchema = "public" // Only look at the public schema (paperless lives there)
        includes = ".*" // What to include (regex).

        // Optional: exclude stuff you don't care about
        excludes = """
                    flyway_schema_history
                  | paperless_rag_.*       # your own RAG schema, if you don't want it here
                """.trimIndent()
      }

      target {
        // Where generated code ends up
        packageName = "com.functionaldude.paperless.jooq"
        directory = "$buildDir/generated-sources/jooq"
      }

      // optional fine-tuning, good defaults also work
      generate {
        isDaos = false
        isPojos = true
        isImmutablePojos = true
        isFluentSetters = true
      }
    }
  }
}

