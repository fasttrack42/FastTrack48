.PHONY: release debug pull tasks find repo one install clean

JAVA_HOME := /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
APK_RELEASE := app/build/outputs/apk/release/FastTrack-release.apk
APK_DEBUG := app/build/outputs/apk/debug/FastTrack-debug.apk

release:
	./gradlew --no-daemon assembleRelease

debug:
	./gradlew --no-daemon assembleDebug

repo:
	open https://github.com/fasttrack48/FastTrack48

tasks:
	./gradlew --no-daemon -q :tasks

install:
	adb install $(APK_RELEASE)

install-debug:
	adb install $(APK_DEBUG)

clean:
	./gradlew --no-daemon clean

check-jetifier:
	./gradlew checkJetifier

test:
	./gradlew app:testDebugUnitTest
