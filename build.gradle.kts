import org.jooq.meta.jaxb.Logging.WARN
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
  id("org.jooq.jooq-codegen-gradle") version "3.19.26"
}

group = "com.functionaldude"
version = "0.0.1-SNAPSHOT"
description = "API for a custom GPT & Paperless"

java {
	toolchain {
    languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.aspectj:aspectjweaver")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.modulith:spring-modulith-starter-core")

  implementation(libs.postgresql)
  implementation("com.pgvector:pgvector:0.1.6")
  implementation("org.flywaydb:flyway-database-postgresql")
  jooqCodegen(libs.postgresql)

  implementation("dev.langchain4j:langchain4j")
  implementation("dev.langchain4j:langchain4j-open-ai")
  implementation("dev.langchain4j:langchain4j-http-client")
  implementation("dev.langchain4j:langchain4j-http-client-jdk")
  implementation("dev.langchain4j:langchain4j-ollama")
  implementation("dev.langchain4j:langchain4j-pgvector")
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
  implementation("org.springaicommunity:mcp-server-security:0.1.14")
  implementation("org.apache.pdfbox:pdfbox:3.0.8")

  developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.h2database:h2")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.1")
    mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
    mavenBom("dev.langchain4j:langchain4j-bom:1.19.5")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

val jooqSources = "src/main/jooq"

sourceSets {
  named("main") {
    kotlin.srcDir(jooqSources)
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

tasks.named<BootBuildImage>("bootBuildImage") {
  // The tiny run image has no locale data; Java then rejects non-ASCII filenames.
  runImage.set("paketobuildpacks/ubuntu-noble-run:latest")
  environment.put("BPE_OVERRIDE_LANG", "en_US.utf8")
  environment.put("BPE_OVERRIDE_LC_ALL", "en_US.utf8")
}

tasks.test {
  useJUnitPlatform {
    excludeTags("integration")
  }
}

tasks.register<Test>("integrationTest") {
  description = "Runs integration tests against the configured Paperless database and embedding service."
  group = "verification"
  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath
  useJUnitPlatform {
    includeTags("integration")
  }
  shouldRunAfter(tasks.test)
  // Provide .env values to the test JVM when not already set in the environment.
  dotenv.forEach { (key, value) ->
    if (System.getenv(key) == null) {
      environment(key, value)
    }
  }
}

jooq {
  configuration {
    logging = WARN

    jdbc {
      driver = "org.postgresql.Driver"
      url = env("PAPERLESS_DB_URL", "jdbc:postgresql://localhost:5432/paperless")
      user = env("PAPERLESS_DB_USER", "paperless")
      password = env("PAPERLESS_DB_PASSWORD", "paperless")
    }

    generator {
      name = "org.jooq.codegen.KotlinGenerator"

      database {
        // Postgres database introspection
        name = "org.jooq.meta.postgres.PostgresDatabase"
        schemata {
          schema {
            inputSchema = "public" // paperless lives here
          }
          schema {
            inputSchema = "paperless_rag"
          }
        }
        includes = ".*" // What to include (regex).

        excludes = "flyway_schema_history"

        forcedTypes {
          forcedType {
            // The Java type that generated code will expose.
            // In Kotlin this shows up as FloatArray.
            userType = FloatArray::class.simpleName!!

            // Your binding class (FQCN)
            binding = "com.functionaldude.paperless_customGPT.PGVectorBinding"

            // Match pgvector columns by SQL type name
            includeTypes = "(?i:vector)"

            // Scope it -> don’t accidentally match other stuff
            includeExpression = "paperless_rag\\.document_chunk\\.embedding"
          }
        }
      }

      target {
        // Where generated code ends up
        packageName = "com.functionaldude.paperless.jooq"
        directory = jooqSources
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
