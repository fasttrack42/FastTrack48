import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
	// By id, not alias(libs.plugins...): F-Droid only takes the version from a gradle file
	// in which it finds this literal plugin id. The root build script pins its version.
	id("com.android.application")
	alias(libs.plugins.ksp)
	alias(libs.plugins.kotlin.compose)
}

// make APK filenames follow <archivesName>-<buildType>.apk,
// i.e. FastTrack48-release.apk and FastTrack48-dev.apk
base {
	archivesName.set("FastTrack48")
}

android {
	namespace = "com.legbehindneck.fasttrack48"
	compileSdk = libs.versions.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "com.legbehindneck.fasttrack48"
		minSdk = libs.versions.minSdk.get().toInt()
		targetSdk = libs.versions.targetSdk.get().toInt()
		// Literals, not catalog lookups: F-Droid reads the version with a regex over this
		// file at the release tag and cannot evaluate a catalog reference, so a lookup reads
		// as code 0. `make tag VERSION=MAJOR.MMDD.HH` (scripts/release.sh) rewrites both
		// lines in the commit the tag points to; the code is the name without its dots.
		versionCode = 14100310
		versionName = "14.1003.10"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}
	signingConfigs {
		create("release") {
			storeFile = file(System.getenv("KEYSTORE") ?: "keystore.jks")
			storePassword = System.getenv("STORE_PASSWORD") ?: ""
			keyAlias = System.getenv("KEY_ALIAS") ?: ""
			keyPassword = System.getenv("KEY_PASSWORD") ?: ""
		}
	}
	buildFeatures {
		buildConfig = true
		compose = true
		resValues = true
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			// Signed only when a keystore is supplied (Makefile, CI). F-Droid builds without
			// one and signs the APK itself, so an unsigned release must not fail the build.
			if (System.getenv("KEYSTORE") != null) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
		debug {
			applicationIdSuffix = ".dev"
			resValue("string", "app_name", "FastTrackDev")
		}
	}
	// A per-app language picker is pointless if Play only installs the device language's
	// resources. Language splits stay off so every translation ships in the base module.
	bundle {
		language {
			enableSplit = false
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
		targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
	}
	dependenciesInfo {
		includeInApk = false
		includeInBundle = false
	}
	testOptions {
		animationsDisabled = true
		managedDevices {
			allDevices {
				create<ManagedVirtualDevice>("pixel6Api34") {
					device = "Pixel 6"
					apiLevel = 34
					systemImageSource = "aosp-atd" // super-lightweight, headless
				}
			}
		}
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.javaVersion.get()))
		freeCompilerArgs.addAll(
			"-opt-in=kotlin.time.ExperimentalTime",
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
		)
	}
}

// F-Droid's dependency scanner enumerates every resolvable Gradle configuration and flags
// io.opencensus:* as a Tracker. It arrives through com.google.testing.platform:launcher, in
// the `_internal-unified-test-platform-*` configurations AGP registers unconditionally on
// every Android module -- not through anything declared below, and never on
// releaseRuntimeClasspath. UTP has been mandatory since AGP 8.0, so the configurations cannot
// be removed; excluding the group from them takes the coordinates out of the graph the
// scanner reads. OpenCensus is an optional gRPC tracing bridge, absent from the shipped APK
// either way. If a host-side UTP run ever needs it, this is the line to revert.
configurations.matching { it.name.startsWith("_internal-unified-test-platform") }
	.configureEach {
		exclude(group = "io.opencensus")
	}

ksp {
	arg("room.schemaLocation", "$projectDir/schemas")
}

android {
	// Include the exported Room schemas in androidTest assets
	sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

dependencies {
	implementation(libs.kotlinx.datetime)
	implementation(libs.core.ktx)
	implementation(libs.activity)
	implementation(libs.collection)

	// AppCompatActivity, its DayNight theme, and the per-app language store below API 33.
	implementation(libs.appcompat)

	implementation(libs.androidx.lifecycle.viewmodel)

	implementation(libs.kotlinx.coroutines.android)

	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.material3.android)
	implementation(libs.androidx.adaptive)

	ksp(libs.androidx.room.compiler)

	implementation(libs.napier)

	implementation(project.dependencies.platform(libs.koin.bom))
	implementation(libs.koin.core)
	implementation(libs.koin.android)
	implementation(libs.koin.androidx.compose)

	implementation(libs.satchel.core)

	implementation(libs.compose.ui)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.foundation)
	implementation(libs.activity.compose)
	implementation(libs.compose.material.icons.extended)
	debugImplementation(libs.compose.ui.tooling)

	// The calendar declares ui-tooling (Android Studio's preview runtime) as a runtime dependency,
	// which would ship in release with an exported PreviewActivity. Debug gets it back above.
	implementation(libs.kizitonwose.calendar.compose) {
		exclude(group = "androidx.compose.ui", module = "ui-tooling")
	}

	// The WorkManager Configuration the app provides for Glance. It used to arrive only
	// transitively; pinned to the version that resolved.
	implementation(libs.androidx.work.runtime)
	implementation(libs.glance)
	implementation(libs.glance.appwidget)
	debugImplementation(libs.glance.preview)
	debugImplementation(libs.glance.appwidget.preview)

	// Removed direct dependencies had raised these transitive libraries (Glance's DataStore,
	// AppCompat's DrawerLayout) above what their own dependents ask for. The constraints hold them
	// at the versions already shipped: DataStore holds the widget's state on disk, and nothing
	// gains from moving the rest backwards.
	constraints {
		implementation(libs.pin.customview)
		implementation(libs.pin.datastore.preferences)
		implementation(libs.pin.drawerlayout)
	}

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)

	androidTestImplementation(libs.androidx.compose.ui.test)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}
