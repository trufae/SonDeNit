VERSION := 0.7.2
APK_NAME := SonDeNit-$(VERSION).apk
RELEASE_APK := app/build/outputs/apk/release/app-release-unsigned.apk
ALIGNED_APK := app/build/outputs/apk/release/app-release-aligned.apk
SDK_DIR := $(shell sed -n 's/^sdk.dir=//p' local.properties)
BUILD_TOOLS_VERSION := 36.0.0
BUILD_TOOLS := $(SDK_DIR)/build-tools/$(BUILD_TOOLS_VERSION)
ZIPALIGN := $(BUILD_TOOLS)/zipalign
APKSIGNER := $(BUILD_TOOLS)/apksigner
SIGNING_KEYSTORE ?= $(HOME)/.android/debug.keystore
SIGNING_KEY_ALIAS ?= androiddebugkey
SIGNING_KEYSTORE_PASS ?= android
SIGNING_KEY_PASS ?= android

.PHONY: release

release:
	./gradlew :app:assembleRelease
	$(ZIPALIGN) -p -f 4 $(RELEASE_APK) $(ALIGNED_APK)
	$(APKSIGNER) sign \
		--ks $(SIGNING_KEYSTORE) \
		--ks-key-alias $(SIGNING_KEY_ALIAS) \
		--ks-pass pass:$(SIGNING_KEYSTORE_PASS) \
		--key-pass pass:$(SIGNING_KEY_PASS) \
		--v1-signing-enabled true \
		--v2-signing-enabled true \
		--v3-signing-enabled true \
		--v4-signing-enabled false \
		--out $(APK_NAME) \
		$(ALIGNED_APK)
	$(APKSIGNER) verify --verbose $(APK_NAME)

install:
	adb install $(APK_NAME)
