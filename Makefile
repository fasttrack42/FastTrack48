# FastTrack48 — build and Google Play release artifacts.
#
#   make            list the targets
#   make bundle     the signed .aab Play actually wants
#   make dist       every uploadable artifact, staged under dist/
#   make preflight  validate the release against Play's rules before uploading
#
# Signing is driven entirely by the environment (see app/build.gradle.kts):
# KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Nothing secret
# lives in this file, and dist/ is git-ignored.
#
# `help` is generated from this file: a `## text` trailer on a target documents
# it, and a `# ---- name` line opens a section. An undocumented target stays
# hidden, which is how the internal plumbing below keeps out of the listing.

SHELL := /bin/bash
.DEFAULT_GOAL := help

# AGP 9 refuses to run on a JVM older than 17, and /usr/bin/java here is 11.
# A plain assignment would not reach the recipes, so this is exported.
export JAVA_HOME := /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

GRADLE       := ./gradlew --no-daemon
PYTHON       := python3
TOML         := gradle/libs.versions.toml
VERSION_CODE := $(shell sed -n 's/^versionCode *= *"\(.*\)"/\1/p' $(TOML))
VERSION_NAME := $(shell sed -n 's/^versionName *= *"\(.*\)"/\1/p' $(TOML))

# Play App Signing keeps the app signing key; what you upload with must be a
# DIFFERENT, app-specific upload key. This is the single source of truth for
# which key every signed target uses — override on the command line if needed.
UPLOAD_KEYSTORE ?= $(HOME)/.android-keystores/fasttrack48-upload.jks
UPLOAD_ALIAS    ?= fasttrack48-upload

# app/build.gradle.kts reads KEYSTORE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
# from the environment. Those names are generic enough to collide with whatever
# is already exported in a login shell, so every signed target sets them
# explicitly here instead of inheriting them. Only the passwords come from the
# environment, and they are dereferenced by the recipe's shell rather than by
# make, so no secret ever enters a make variable or an echoed command line.
SIGN_ENV = KEYSTORE="$(UPLOAD_KEYSTORE)" KEY_ALIAS="$(UPLOAD_ALIAS)" \
	STORE_PASSWORD="$${STORE_PASSWORD:-$$KEYSTORE_PASSWORD}" \
	KEY_PASSWORD="$${KEY_PASSWORD:-$${STORE_PASSWORD:-$$KEYSTORE_PASSWORD}}"

OUTPUTS      := app/build/outputs
APK_RELEASE  := $(OUTPUTS)/apk/release/FastTrack48-release.apk
APK_DEBUG    := $(OUTPUTS)/apk/debug/FastTrack48-debug.apk
AAB_RELEASE  := $(OUTPUTS)/bundle/release/FastTrack48-release.aab
MAPPING      := $(OUTPUTS)/mapping/release/mapping.txt
NATIVE_SYMS  := $(OUTPUTS)/native-debug-symbols/release/native-debug-symbols.zip

DIST         := dist/$(VERSION_CODE)

.PHONY: help version release debug bundle install install-debug test lint \
        icons preflight dist clean clean-dist tasks check-jetifier \
        verify-signing play-internal play-production play-metadata \
        require-signing upload-key key-fingerprints deps

help: ## Show this help
	@printf '\033[1mFastTrack48\033[0m %s (versionCode %s)\n' \
		'$(VERSION_NAME)' '$(VERSION_CODE)'
	@awk 'BEGIN {FS = ":.*?## "} \
		/^# -+ [a-z]/ { sub(/^# -+ /, ""); pending = $$0; next } \
		/^[a-zA-Z0-9_-]+:.*?## / { \
			if (pending != "") { printf "\n  \033[1m%s\033[0m\n", pending; pending = "" } \
			printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' \
		$(MAKEFILE_LIST)
	@echo

# ---- build

debug: ## Assemble the debug APK
	$(GRADLE) assembleDebug
	@echo $(APK_DEBUG)

release: require-signing ## Assemble the signed release APK (sideloading only)
	$(SIGN_ENV) $(GRADLE) assembleRelease
	@echo $(APK_RELEASE)

bundle: require-signing ## Assemble the signed release AAB — the Play upload
	$(SIGN_ENV) $(GRADLE) bundleRelease

test: ## Run the unit tests
	$(GRADLE) app:testDebugUnitTest

lint: ## Run Android lint against the release variant
	$(GRADLE) app:lintRelease

# Gradle will happily sign with an empty password and produce an artifact Play
# rejects minutes later, so the keystore, the alias and the password are all
# proven to work together before the build starts.
require-signing:
	@if [[ ! -f "$(UPLOAD_KEYSTORE)" ]]; then \
		echo "upload keystore not found: $(UPLOAD_KEYSTORE)" >&2; \
		echo "run 'make upload-key', or pass UPLOAD_KEYSTORE=<path>" >&2; \
		exit 1; \
	fi
	@export STOREPASS="$${STORE_PASSWORD:-$$KEYSTORE_PASSWORD}"; \
	if [[ -z "$$STOREPASS" ]]; then \
		echo "no keystore password: export STORE_PASSWORD (or KEYSTORE_PASSWORD)" >&2; \
		exit 1; \
	fi; \
	if ! keytool -list -keystore "$(UPLOAD_KEYSTORE)" -storepass:env STOREPASS \
		-alias "$(UPLOAD_ALIAS)" >/dev/null 2>&1; then \
		echo "cannot open alias '$(UPLOAD_ALIAS)' in $(UPLOAD_KEYSTORE)" >&2; \
		echo "wrong password, or the alias does not exist in that keystore" >&2; \
		exit 1; \
	fi; \
	echo "signing with $(UPLOAD_KEYSTORE) (alias $(UPLOAD_ALIAS))"

# ---- signing

# Generates the upload key. It never touches an existing file, and keytool
# prompts for the passwords so none of them land in the shell history.
upload-key: ## Generate a new Play upload key and export its certificate
	@if [[ -e "$(UPLOAD_KEYSTORE)" ]]; then \
		echo "$(UPLOAD_KEYSTORE) already exists; refusing to overwrite it." >&2; \
		echo "Delete it deliberately, or set UPLOAD_KEYSTORE=<path>." >&2; \
		exit 1; \
	fi
	@mkdir -p "$$(dirname "$(UPLOAD_KEYSTORE)")"
	keytool -genkeypair \
		-keystore "$(UPLOAD_KEYSTORE)" -storetype PKCS12 \
		-alias "$(UPLOAD_ALIAS)" \
		-keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
		-validity 14600 \
		-dname "CN=FastTrack48 Upload Key, O=FastTrack48, C=US"
	keytool -export -rfc \
		-keystore "$(UPLOAD_KEYSTORE)" -alias "$(UPLOAD_ALIAS)" \
		-file upload_certificate.pem
	@chmod 600 "$(UPLOAD_KEYSTORE)"
	@echo
	@echo "upload key:  $(UPLOAD_KEYSTORE)"
	@echo "certificate: $$PWD/upload_certificate.pem"
	@echo
	@echo "Register upload_certificate.pem in Play Console:"
	@echo "  Test and release > Setup > App integrity > App signing"
	@echo "The Makefile already points every signed target at this key;"
	@echo "only the password has to reach it:"
	@echo "  export STORE_PASSWORD=...   # KEY_PASSWORD defaults to the same"

key-fingerprints: ## Print the fingerprints of the key the build will use
	@echo "keystore: $(UPLOAD_KEYSTORE)"
	@export STOREPASS="$${STORE_PASSWORD:-$$KEYSTORE_PASSWORD}"; \
	keytool -list -v -keystore "$(UPLOAD_KEYSTORE)" \
		-storepass:env STOREPASS -alias "$(UPLOAD_ALIAS)" 2>&1 \
		| grep -E "Alias name|Creation date|Owner|Valid from|SHA1:|SHA256:|Signature algorithm"

# ---- play

icons: ## Regenerate every icon and storefront asset
	$(PYTHON) scripts/icon/gen_icon.py

# Everything Play or a GitHub release could ask for, in one versioned folder.
# The AAB already embeds the ProGuard mapping as bundle metadata, but a loose
# copy is staged too so crash reports can be deobfuscated without unzipping it.
dist: bundle ## Stage all uploadable artifacts under dist/<versionCode>/
	@rm -rf $(DIST) && mkdir -p $(DIST)/listing/screenshots
	@cp $(AAB_RELEASE) $(DIST)/FastTrack48-$(VERSION_NAME)-$(VERSION_CODE).aab
	@[[ -f $(APK_RELEASE) ]] && cp $(APK_RELEASE) \
		$(DIST)/FastTrack48-$(VERSION_NAME)-$(VERSION_CODE).apk || true
	@[[ -f $(MAPPING) ]] && cp $(MAPPING) $(DIST)/mapping-$(VERSION_CODE).txt || true
	@[[ -f $(NATIVE_SYMS) ]] && cp $(NATIVE_SYMS) $(DIST)/ || true
	@cp fastlane/metadata/android/en-US/images/icon.png $(DIST)/listing/icon-512.png
	@cp fastlane/metadata/android/en-US/images/featureGraphic.png \
		$(DIST)/listing/feature-graphic-1024x500.png
	@cp fastlane/metadata/android/en-US/images/phoneScreenshots/* \
		$(DIST)/listing/screenshots/
	@cp fastlane/metadata/android/en-US/title.txt \
		fastlane/metadata/android/en-US/short_description.txt \
		fastlane/metadata/android/en-US/full_description.txt $(DIST)/listing/
	@cp fastlane/metadata/android/en-US/changelogs/$(VERSION_CODE).txt \
		$(DIST)/listing/release-notes.txt 2>/dev/null || \
		echo "note: no changelog for versionCode $(VERSION_CODE)" >&2
	@cp LICENSE $(DIST)/
	@shasum -a 256 $(DIST)/*.aab $(DIST)/*.apk 2>/dev/null > $(DIST)/SHA256SUMS || true
	@echo; echo "staged in $(DIST):"; ls -1 $(DIST)
	@$(MAKE) --no-print-directory preflight

preflight: ## Validate listing and artifacts against Play's rules
	@$(PYTHON) scripts/play/preflight.py

# v2 is the floor for the API 30+ requirement; v3 enables key rotation. Both
# come from the keystore configuration, so this reports rather than enforces.
verify-signing: ## Show the signature scheme of the built artifacts
	@build_tools=$$(ls -1d "$$HOME/Library/Android/sdk/build-tools"/* 2>/dev/null | sort -V | tail -1); \
	if [[ -z "$$build_tools" ]]; then echo "no build-tools installed" >&2; exit 1; fi; \
	for f in $(APK_RELEASE) $(AAB_RELEASE); do \
		[[ -f "$$f" ]] || continue; \
		echo "== $$f"; \
		"$$build_tools/apksigner" verify --verbose "$$f" 2>&1 | head -20 || true; \
	done

play-internal: dist ## Upload bundle and listing to the internal track
	bundle exec fastlane android internal

play-production: dist ## Upload bundle and listing straight to production
	bundle exec fastlane android deploy

play-metadata: ## Upload listing text and graphics only, no binary
	bundle exec fastlane android metadata

# ---- misc

install: ## adb install the release APK
	adb install -r $(APK_RELEASE)

install-debug: ## adb install the debug APK
	adb install -r $(APK_DEBUG)

version: ## Print the current version name and code
	@echo "$(VERSION_NAME) ($(VERSION_CODE))"

tasks: ## List the Gradle tasks
	$(GRADLE) -q :tasks

check-jetifier: ## Check whether any dependency still needs Jetifier
	$(GRADLE) checkJetifier

deps: ## print dependency tree
	./gradlew :app:dependencies

clean: ## Gradle clean
	$(GRADLE) clean

clean-dist: ## Remove the staged dist/ artifacts
	rm -rf dist

fix-images: ## fix images for fastlane
	find fastlane/metadata/android/en-US/images -iname '*.png' -exec python3 ./scripts/play/pad_screenshots.py {} \;
