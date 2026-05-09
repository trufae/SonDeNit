VERSION := 0.2
APK_NAME := SonDeNit-$(VERSION).apk
RELEASE_APK := app/build/outputs/apk/release/app-release-unsigned.apk

.PHONY: release

release:
	./gradlew :app:assembleRelease
	cp $(RELEASE_APK) $(APK_NAME)
