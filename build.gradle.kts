buildscript {
	repositories {
		jcenter()
	}
	dependencies {
		classpath("com.guardsquare:proguard-gradle:7.0.0") {
			exclude("com.android.tools.build")
		}
	}
}

plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "6.1.0"
	id("com.palantir.git-version") version "0.12.3"
	id("com.github.breadmoirai.github-release") version "2.2.12"
	kotlin("jvm") version "1.4.21"
	id("com.github.jk1.dependency-license-report") version "1.16"
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
}

val shrinkClasspath: Configuration by configurations.creating

dependencies {
	implementation("commons-cli:commons-cli:1.4")
	shrinkClasspath("commons-cli:commons-cli:1.4")
	implementation("com.moandjiezana.toml:toml4j:0.7.2")
	implementation("com.google.code.gson:gson:2.9.0")
	implementation("com.squareup.okio:okio:2.9.0")
	implementation(kotlin("stdlib-jdk8"))
	//implementation("org.apache.httpcomponents:httpclient:4.5.13")
}

repositories {
	jcenter()
	mavenCentral()
}

application {
	mainClassName = "link.infra.packwiz.installer.RequiresBootstrap"
}

val gitVersion: groovy.lang.Closure<*> by extra
//version = gitVersion()
version = "v0.3.3"

tasks.jar {
	manifest {
		attributes["Main-Class"] = "link.infra.packwiz.installer.RequiresBootstrap"
		attributes["Implementation-Version"] = project.version
	}
}

licenseReport {
	renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
		com.github.jk1.license.render.InventoryMarkdownReportRenderer("licenses.md", "packwiz-installer")
	)
	filters = arrayOf<com.github.jk1.license.filter.DependencyFilter>(com.github.jk1.license.filter.LicenseBundleNormalizer())
}

// TODO: build relocated jar for minecraft launcher lib, non-relocated jar for packwiz-installer
//tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation>("relocateShadowJar") {
//	target = tasks.shadowJar.get()
//	prefix = "link.infra.packwiz.deps"
//}

// Commons CLI and Minimal JSON are already included in packwiz-installer-bootstrap
tasks.shadowJar {
	dependencies {
		exclude(dependency("commons-cli:commons-cli:1.4"))
		exclude(dependency("com.eclipsesource.minimal-json:minimal-json:0.9.5"))
		// TODO: exclude meta inf files
	}
	exclude("**/*.kotlin_metadata")
	exclude("**/*.kotlin_builtins")
	exclude("META-INF/maven/**/*")
	exclude("META-INF/proguard/**/*")
	//dependsOn(tasks.named("relocateShadowJar"))
}

tasks.register<proguard.gradle.ProGuardTask>("shrinkJar") {
	injars(tasks.shadowJar)
	libraryjars(files(shrinkClasspath.files))
	outjars("build/libs/" + tasks.shadowJar.get().outputs.files.first().name.removeSuffix(".jar") + "-shrink.jar")
	if (System.getProperty("java.version").startsWith("1.")) {
		libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
		libraryjars("${System.getProperty("java.home")}/lib/jce.jar")
	} else {
		throw RuntimeException("Compiling with Java 9+ not supported!")
	}

	keep("class link.infra.packwiz.installer.** { *; }")
	dontoptimize()
	dontobfuscate()
	dontwarn("org.codehaus.mojo.animal_sniffer.*")
}

// Used for vscode launch.json
tasks.register<Copy>("copyJar") {
	from(tasks.named("shrinkJar"))
	rename("packwiz-installer-(.*)\\.jar", "packwiz-installer.jar")
	into("build/libs/")
}

tasks.build {
	dependsOn("copyJar")
}

if (project.hasProperty("github.token")) {
	githubRelease {
		owner("comp500")
		repo("packwiz-installer")
		tagName("${project.version}")
		releaseName("Release ${project.version}")
		draft(true)
		token(findProperty("github.token") as String? ?: "")
		releaseAssets(tasks.jar.get().destinationDirectory.file("packwiz-installer.jar").get())
	}

	tasks.githubRelease {
		dependsOn(tasks.build)
	}
}

tasks.compileKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable")
	}
}
tasks.compileTestKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable")
	}
}