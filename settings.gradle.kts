pluginManagement {
	repositories {
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
// Configure project settings
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		// No JitPack: its only user, Satchel, is gone, and JitPack builds artifacts on demand from
		// GitHub, which no one signs or reviews.
	}
}

rootProject.name = "FastTrack48"
include(":app")