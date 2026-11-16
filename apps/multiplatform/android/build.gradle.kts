@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 36

    defaultConfig {
        // L'identifiant de distribution est celui de Trustiatis : une version
        // dérivée ne peut pas porter celui de l'amont, ni sur un magasin, ni sur
        // un appareil où les deux doivent pouvoir cohabiter.
        applicationId = "com.trustiatis.secure"
        // Le namespace reste celui de l'amont : il ne nomme que les classes R et
        // BuildConfig, il n'est pas vu de l'utilisateur, et le changer obligerait
        // à déplacer les sources Kotlin, donc à rendre chaque report conflictuel.
        namespace = "chat.simplex.app"
        minSdk = 26
        targetSdk = 36
        // !!!
        // skip version code after release to F-Droid, as it uses two version codes
        versionCode = (extra["android.version_code"] as String).toInt()
        versionName = extra["android.version_name"] as String

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
        manifestPlaceholders["app_name"] = "@string/app_name"
        manifestPlaceholders["provider_authorities"] = "com.trustiatis.secure.provider"
        manifestPlaceholders["extract_native_libs"] = rootProject.extra["compression.level"] as Int != 0
    }

    // `google` is distributed via Google Play as an app bundle and includes Play Billing.
    // `foss` is distributed via F-Droid and as APKs on GitHub, without Play dependencies.
    flavorDimensions += "store"
    productFlavors {
        create("google") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
        create("foss") {
            dimension = "store"
            isDefault = true
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
    }

    // Signature de version. Le chemin de l'amont ne sert pas ici : il passe par
    // scripts/android/compress-and-sign-apk.sh, qui exige la commande `zip`, et il ne
    // s'exécute que si compression.level n'est pas nul, ce qui n'est pas le cas sur un
    // poste sans `zip`. Cette configuration ci signe par le greffon Android lui même.
    //
    // **Aucun secret n'entre dans le dépôt**, ce qu'interdit le chapitre 6 du cahier des
    // charges et CONTRIBUTING.md : ni chemin de magasin, ni mot de passe, ni alias en dur.
    // Tout est lu dans l'environnement, que l'opérateur pose dans son shell et que la CI
    // injecte par variables, depuis KeePassXC puis Vault.
    //
    // Sans TRUSTIATIS_KEYSTORE, aucune configuration n'est créée et la compilation de
    // débogage continue de fonctionner comme avant : la signature de version est alors
    // simplement absente, et le greffon le dira.
    signingConfigs {
        val magasin = System.getenv("TRUSTIATIS_KEYSTORE")
        if (!magasin.isNullOrBlank()) {
            create("trustiatis") {
                storeFile = File(magasin)
                storePassword = System.getenv("TRUSTIATIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TRUSTIATIS_KEY_ALIAS") ?: "trustiatis"
                // Un magasin créé par keytool sans mot de passe de clé distinct reprend
                // celui du magasin : c'est le cas courant, et le défaut le reflète.
                keyPassword = System.getenv("TRUSTIATIS_KEY_PASSWORD")
                    ?: System.getenv("TRUSTIATIS_KEYSTORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = rootProject.extra["application_id.suffix"] as String
            isDebuggable = rootProject.extra["enable_debuggable"] as Boolean
            manifestPlaceholders["app_name"] = rootProject.extra["app.name"] as String
            // Provider can"t be the same for different apps on the same device
            manifestPlaceholders["provider_authorities"] = "com.trustiatis.secure${rootProject.extra["application_id.suffix"]}.provider"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("trustiatis")?.let { signingConfig = it }
        }
    }
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        freeCompilerArgs += "-opt-in=androidx.compose.ui.text.ExperimentalTextApi"
        freeCompilerArgs += "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        freeCompilerArgs += "-opt-in=com.google.accompanist.insets.ExperimentalAnimatedInsets"
        freeCompilerArgs += "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.InternalSerializationApi"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
    externalNativeBuild {
        cmake {
            path(File("../common/src/commonMain/cpp/android/CMakeLists.txt"))
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = rootProject.extra["compression.level"] as Int != 0
    }
    android.sourceSets["main"].assets.setSrcDirs(listOf("../common/src/commonMain/resources/assets"))
    val isRelease = gradle.startParameter.taskNames.find { it.lowercase().contains("release") } != null
    val isBundle = gradle.startParameter.taskNames.find { it.lowercase().contains("bundle") } != null
    // Comma separated list of languages that will be included in the apk
    android.defaultConfig.resourceConfigurations += listOf(
        "en",
        "ar",
        "bg",
        "ca",
        "cs",
        "de",
        "es",
        "fa",
        "fi",
        "fr",
        "hu",
        "in",
        "it",
        "iw",
        "ja",
        "lt",
        "nl",
        "pl",
        "pt-rBR",
        "ro",
        "ru",
        "th",
        "tr",
        "uk",
        "vi",
        "zh-rCN"
    )
    ndkVersion = "23.1.7779620"
    if (isBundle) {
        defaultConfig.ndk.abiFilters("arm64-v8a", "armeabi-v7a")
    } else {
        splits {
            abi {
                isEnable = true
                reset()
                if (isRelease) {
                    include("arm64-v8a", "armeabi-v7a")
                } else {
                    include("arm64-v8a", "armeabi-v7a")
                    isUniversalApk = false
                }
            }
        }
    }
}

// The graph is checked rather than the requested task, because every aggregate task
// (assemble, assembleRelease, build, bundle, ...) packages these variants too.
val projectPath = project.path
val apkTasks = setOf("packageFossDebug", "packageGoogleDebug", "packageFossRelease", "packageGoogleRelease")
val apkTaskPaths = apkTasks.map { "$projectPath:$it" }.toSet()
val bundleTaskPaths = apkTaskPaths.map { it + "Bundle" }.toSet()
gradle.taskGraph.whenReady {
    if (hasTask("$projectPath:packageGoogleRelease")) {
        throw GradleException("A release apk must not include Play Billing, use assembleFossRelease or bundleGoogleRelease")
    }
    if (hasTask("$projectPath:packageFossReleaseBundle")) {
        throw GradleException("An app bundle must include Play Billing, use bundleGoogleRelease or assembleFossRelease")
    }
    // `isBundle` above is derived from the whole invocation, so a bundle in it disables abi splits
    if (apkTaskPaths.any { hasTask(it) } && bundleTaskPaths.any { hasTask(it) }) {
        throw GradleException("Build the apks and the bundle in separate invocations, the bundle disables abi splits")
    }
}

dependencies {
    implementation(project(":common"))
    "googleImplementation"("com.android.billingclient:billing:9.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    //implementation("androidx.compose.ui:ui:${rootProject.extra["compose.version"] as String}")
    //implementation("androidx.compose.material:material:$compose_version")
    //implementation("androidx.compose.ui:ui-tooling-preview:$compose_version")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    val workVersion = "2.9.1"
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("androidx.work:work-multiprocess:$workVersion")

    implementation("com.jakewharton:process-phoenix:3.0.0")

    //Camera Permission
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    //implementation("androidx.compose.material:material-icons-extended:$compose_version")
    //implementation("androidx.compose.ui:ui-util:$compose_version")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    //androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.4")
}

tasks {
    val compressApk by creating {
        doLast {
            val javaHome = System.getProperties()["java.home"] ?: org.gradle.internal.jvm.Jvm.current().javaHome
            val sdkDir = android.sdkDirectory.absolutePath
            // A single invocation can package more than one variant, for example assembleDebug
            gradle.taskGraph.allTasks.filter { it.path in apkTaskPaths }.forEach { packageTask ->
                val variant = packageTask.name.removePrefix("package")
                val buildType: String = if (variant.endsWith("Release")) "release" else "debug"
                val keyAlias: String
                val keyPassword: String
                val storeFile: String
                val storePassword: String
                if (project.properties["android.injected.signing.key.alias"] != null) {
                    keyAlias = project.properties["android.injected.signing.key.alias"] as String
                    keyPassword = project.properties["android.injected.signing.key.password"] as String
                    storeFile = project.properties["android.injected.signing.store.file"] as String
                    storePassword = project.properties["android.injected.signing.store.password"] as String
                } else {
                    try {
                        val gradleConfig = android.signingConfigs.getByName(buildType)
                        keyAlias = gradleConfig.keyAlias!!
                        keyPassword = gradleConfig.keyPassword!!
                        storeFile = gradleConfig.storeFile!!.absolutePath
                        storePassword = gradleConfig.storePassword!!
                    } catch (e: UnknownDomainObjectException) {
                        // There is no signing config for current build type, can"t sign the apk
                        println("No signing configs for this build type: $buildType")
                        return@forEach
                    }
                }
                val outputDir = packageTask.outputs.files.files.last()
                exec {
                    workingDir("../../scripts/android")
                    environment = mapOf(
                      "JAVA_HOME" to "$javaHome",
                      "PATH" to "${System.getenv("PATH")}:$javaHome/bin"
                    )
                    commandLine = listOf(
                        "./compress-and-sign-apk.sh",
                        "${rootProject.extra["compression.level"]}",
                        "$outputDir",
                        sdkDir,
                        storeFile,
                        storePassword,
                        keyAlias,
                        keyPassword
                    )
                }

                if (project.properties["android.injected.signing.key.alias"] != null && buildType == "release") {
                    val flavor = variant.removeSuffix("Release").lowercase()
                    mapOf("arm64-v8a" to "trustiatis-secure.apk", "armeabi-v7a" to "trustiatis-secure-armv7a.apk").forEach { (abi, name) ->
                        if (!File(outputDir, "android-$flavor-$abi-release.apk").renameTo(File(outputDir, name))) {
                            logger.warn("No $abi apk to rename to $name")
                        }
                    }
                }
            }
            // View all gradle properties set
            // project.properties.each { k, v -> println "$k -> $v" }
        }
    }

    // Don"t do anything if no compression is needed
    if (rootProject.extra["compression.level"] as Int != 0) {
        whenTaskAdded {
            if (name in apkTasks) {
                finalizedBy(compressApk)
            }
        }
    }
}
