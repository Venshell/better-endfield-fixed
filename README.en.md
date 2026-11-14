# Better Endfield Next for Android

[English](README.en.md) · [简体中文](README.md)

Better Endfield is an Android LSPosed module for *Arknights: Endfield*, with a settings app and an in-game control panel. Features are implemented by separate modules. Android is the project's only current development and release target. Windows code and build scripts remain in the repository as legacy material; they are outside the current product and acceptance scope. See the [product boundary](docs/PRODUCT_BOUNDARY.md), the [changelog](CHANGELOG.md), and release notes for validation status.

> This is an independently maintained derivative of [Dr-hydra/Better-Endfield](https://github.com/Dr-hydra/Better-Endfield). It is not an official release of the upstream project or the game publisher.

[![Android Release 4.2.0](https://img.shields.io/badge/Android%20Release-4.2.0-26CDCB)](https://github.com/Venshell/better-endfield-fixed/releases/latest)
[![Experimental](https://img.shields.io/badge/Experimental-glide%20%2B%20link-E85327)](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive)
[![Changelog](https://img.shields.io/badge/Docs-Changelog-1C4A9C)](CHANGELOG.md)
[![Android Guide](https://img.shields.io/badge/Android-Guide-1C4A9C)](android/README.md)
[![BEM Creator Guide](https://img.shields.io/badge/BEM-Creator%20Guide-1C4A9C)](docs/custom_model/BEM_CREATOR_GUIDE.md)

The current Android version is **4.2.0** (`dev.betterendfield.next`). Next installs separately from 3.x and requires its own LSPosed scope. It bundles 898 headwear assets for Android game version 1.6.4 (versionCode 61). Glide and multiplayer are on the experimental branch [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive).

## Differences from upstream Next

This repository targets Android ARM64 releases. These features and branch choices distinguish it from [upstream](https://github.com/Dr-hydra/Better-Endfield).

- **First-person gyroscope**: steer the first-person view with the phone's gyroscope. Its switch and sensitivity are in the Android settings app.
- **Bundled headwear assets**: the APK contains 898 v3/v61 assets for hiding or replacing meshes that obstruct first-person vision. They target game versionCode 61. Character-by-character results require device validation.
- **Experimental branch**: glide and multiplayer are on [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive). [Download the experimental APK from that branch's Actions](https://github.com/Venshell/better-endfield-fixed/actions/workflows/android-build.yml?query=branch%3Aexperimental%2Fdestructive). Stable `main` does not include either, and it keeps sustained dash.

The Android settings app and in-game panel use this repository's Compose screens for first person, BEM management, MMD import and reception-room display controls. See the [Android guide](android/README.md) for entry points and the [changelog](CHANGELOG.md) for version changes.

## Features

See the release notes for device validation details.

| Feature | Android ARM64 |
| --- | --- |
| Models and appearance | BEM import and management, appearance and morph parameters, Endministrator weapon appearance, in-game model and animation controls |
| Camera | First person and gyroscope, free camera, global FOV, character following, time freeze and camera paths |
| MMD and reception room display | Work import and slot confirmation, in-game playback, image and video casting |
| Game settings | Home screen game management and feature summary, in-game control panel, UI settings, character voice and sustained dash |

Appearance packages use the standard BEMv1 `.bem` format. See the [BEM creator guide](docs/custom_model/BEM_CREATOR_GUIDE.md) for format and conversion limits.

## Get started

1. Download the APK from the [stable release](https://github.com/Venshell/better-endfield-fixed/releases/latest), and check its compatibility notes and checksum.
2. Install it, enable the module in LSPosed, and select the Endfield package you play.
3. Configure features in the companion app, fully stop the game, then start it again.

See the [Android guide](android/README.md) for setup, compatibility and troubleshooting. Bundled headwear assets target the native Android game at versionCode 61; other client versions need separate verification.

## Build from source

The Android build requires JDK 21, Android SDK 37, NDK 27.2.12479018, CMake 3.22.1, Dobby v1.0.5 and the complete headwear catalog. Follow the [Android build guide](android/README.md#从源码构建); and download the required catalog from the [Android headwear v3 / v61 resource release](https://github.com/Venshell/better-endfield-fixed/releases/tag/headwear-v3-v61).

## Documentation

| Document | Contents |
| --- | --- |
| [Android guide](android/README.md) | Installation, LSPosed scope, builds and troubleshooting |
| [Technical reference](docs/TECHNICAL_DETAILS.md) | Module boundaries, loaders, configuration, logs and build details (Chinese) |
| [Documentation index](docs/README.md) | Current topic guides and historical records (Chinese) |
| [Changelog](CHANGELOG.md) | Releases and validation boundaries |
| [BEM creator guide](docs/custom_model/BEM_CREATOR_GUIDE.md) | Package authoring and validation |
| [Downloads site](web/mod-center/README.md) | QQ Mail registration, mainland-China network limits and database backups (Chinese) |

For bug reports, use [Issues](https://github.com/Venshell/better-endfield-fixed/issues) and include the platform, game and module versions, and reproduction steps. The Android app can export its runtime log; review it for personal information before sharing.

## Origin and license

The project is licensed under [AGPL-3.0-only](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for upstream and dependency attribution. The project is not affiliated with the game publisher. Back up your configuration before use, and check compatibility after game updates.
