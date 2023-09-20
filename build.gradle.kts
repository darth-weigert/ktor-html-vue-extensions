plugins {
    kotlin("multiplatform") version "1.9.0"
    jacoco
    id("maven-publish")
}

group = "dw"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

jacoco {
    toolVersion = "0.8.10"
}

val kotlinxHtmlVersion = "0.9.1"
val ktorVersion = "2.3.4"
val jsoupVersion = "1.16.1"
val assertjVersion = "3.24.2"
val kotestVersion = "5.7.2"

val generatedCodePath = layout.buildDirectory.dir("generated-sources")

val generateCode by tasks.registering(JavaExec::class) {
    group = "codeGenerate"
    classpath = project(":gen").sourceSets["main"].runtimeClasspath
    mainClass.set("dw.GenerateExtKt")
    outputs.dir(generatedCodePath)
    args = listOf("--output", generatedCodePath.get().asFile.toString())

    doLast {
        val sourceDir: File = generatedCodePath.get().asFile
        // ...
    }
}

kotlin {
    jvmToolchain(8)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig(Action {
                cssSupport {
                    enabled.set(true)
                }
            })
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedCodePath)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-html:${kotlinxHtmlVersion}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:${kotestVersion}")
            }
        }
        val ktorTest by creating {
            dependsOn(commonTest)
            kotlin.srcDir("src/ktorTest/kotlin")
            dependencies {
                implementation("io.ktor:ktor-server-tests:${ktorVersion}")
                implementation("io.ktor:ktor-server-html-builder:${ktorVersion}")
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependsOn(ktorTest)
        }
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting {
            dependsOn(ktorTest)
        }
    }
}

tasks["compileKotlinJs"].dependsOn(generateCode)
tasks["compileKotlinJvm"].dependsOn(generateCode)
tasks["compileKotlinNative"].dependsOn(generateCode)

val jacocoTestReport by tasks.getting(JacocoReport::class) {
    val coverageSourceDirs = arrayOf(
        generatedCodePath,
    )

    val classFiles = File("${buildDir}/classes/kotlin/jvm/")
        .walkBottomUp()
        .toSet()

    classDirectories.setFrom(classFiles)
    sourceDirectories.setFrom(files(coverageSourceDirs))

    executionData
        .setFrom(files("${buildDir}/jacoco/jvmTest.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
