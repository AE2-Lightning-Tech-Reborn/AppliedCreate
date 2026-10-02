plugins {
    id("net.neoforged.moddev") version "1.0.15"
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("me.modmuss50.mod-publish-plugin") version "1.1.0"
}

version = project.extra["mod_version"] as String
group = project.extra["mod_group_id"] as String
val modId = project.extra["mod_id"] as String

base {
    archivesName.set("$modId-${project.extra["minecraft_version"]}")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)
kotlin.jvmToolchain(21)

// Runtime fixtures are compiled separately and never enter the mod JAR.
val gameTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + sourceSets.main.get().runtimeClasspath
}
configurations[gameTest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[gameTest.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[gameTest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

neoForge {
    version = project.extra["neoforge_version"] as String

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(gameTest)
        }
    }

    runs {
        create("client") {
            client()
            systemProperty("guideDev.ae2guide.sources", file("src/main/resources/assets/appliedcreate/ae2guide").absolutePath)
            systemProperty("guideDev.ae2guide.sourcesNamespace", "appliedcreate")
        }
        create("server") {
            server()
        }
        create("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gameTest
            gameDirectory = file("run-gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "appliedcreate_stress,appliedcreate_compat,appliedcreate_cannon")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content { includeGroup("thedarkcolour") }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
    maven {
        name = "Create Mod Maven"
        url = uri("https://maven.createmod.net")
    }
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev")
    }
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
    }
    maven {
        name = "CurseMaven"
        url = uri("https://www.cursemaven.com")
        content { includeGroup("curse.maven") }
    }
    maven {
        name = "Registrate"
        url = uri("https://maven.ithundxr.dev/snapshots")
        content { includeGroup("com.tterrag.registrate") }
    }
}

dependencies {
    providers.gradleProperty("compat_test_mods_dir").orNull?.let { directory ->
        add(gameTest.runtimeOnlyConfigurationName, fileTree(directory) { include("*.jar") })
    }
    if (providers.gradleProperty("create_test_jar").isPresent) {
        add(gameTest.runtimeOnlyConfigurationName, files(providers.gradleProperty("create_test_jar").get()))
    } else {
        add(gameTest.runtimeOnlyConfigurationName,
            "com.simibubi.create:create-${project.extra["minecraft_version"]}:${project.extra["create_version"]}") {
            isTransitive = false
        }
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.extra["kotlin_for_forge_version"]}")

    implementation("org.appliedenergistics:appliedenergistics2:${project.extra["ae2_version"]}")
    implementation("org.appliedenergistics:guideme:${project.extra["guideme_version"]}")

    compileOnly("com.simibubi.create:create-${project.extra["minecraft_version"]}:${project.extra["create_version"]}")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-${project.extra["minecraft_version"]}:${project.extra["flywheel_version"]}")
    compileOnly("net.createmod.ponder:ponder-neoforge:${project.extra["ponder_version"]}")
    // Registrate is bundled inside Create's jar-in-jar — needs explicit compileOnly for Kotlin to see it
    compileOnly("com.tterrag.registrate:Registrate:${project.extra["registrate_version"]}")

    // JEI — compile against API only, users install JEI themselves
    compileOnly("mezz.jei:jei-${project.extra["minecraft_version"]}-common-api:${project.extra["jei_version"]}")
    compileOnly("mezz.jei:jei-${project.extra["minecraft_version"]}-neoforge-api:${project.extra["jei_version"]}")

    // AE2 JEI Integration — bridge for AE2 key types in JEI (IngredientConverters API)
    compileOnly("curse.maven:ae2-jei-integration-1074338:7727898")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Specification-Title" to modId,
            "Specification-Vendor" to project.extra["mod_authors"],
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to project.extra["mod_authors"]
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "mod_id" to modId,
        "mod_name" to project.extra["mod_name"],
        "mod_license" to project.extra["mod_license"],
        "mod_version" to project.version,
        "mod_authors" to project.extra["mod_authors"],
        "mod_description" to project.extra["mod_description"],
        "neoforge_version" to project.extra["neoforge_version"],
        "minecraft_version" to project.extra["minecraft_version"],
        "loader_version_range" to project.extra["loader_version_range"],
        "neo_version_range" to project.extra["neo_version_range"],
        "minecraft_version_range" to project.extra["minecraft_version_range"],
        "create_version_range" to project.extra["create_version_range"],
        "ae2_version_range" to project.extra["ae2_version_range"]
    )

    inputs.properties(replaceProperties)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}

// ========== Publish to CurseForge & Modrinth ==========
publishMods {
    file.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("Release ${project.version}"))
    type = STABLE
    modLoaders.add("neoforge")
    displayName.set("${project.extra["mod_name"]} ${project.version} for NeoForge ${project.extra["minecraft_version"]}")
    version.set(project.version.toString())

    curseforge {
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(providers.gradleProperty("CURSEFORGE_TOKEN")))
        projectId.set(project.extra["curseforge_project_id"] as String)
        projectSlug.set(project.extra["mod_id"] as String)
        minecraftVersions.add(project.extra["minecraft_version"] as String)
        clientRequired.set(true)
        serverRequired.set(true)
        requires("create")
        requires("applied-energistics-2")
        requires("kotlin-for-forge")
        optional("jei")
        optional("ae2-jei-integration")
        optional("configured")
    }

    modrinth {
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN")
            .orElse(providers.gradleProperty("MODRINTH_TOKEN")))
        projectId.set(project.extra["modrinth_project_id"] as String)
        minecraftVersions.add(project.extra["minecraft_version"] as String)
        requires("create")
        requires("ae2")
        requires("kotlin-for-forge")
        optional("jei")
        optional("ae2-jei-integration")
        optional("configured")
    }
}
