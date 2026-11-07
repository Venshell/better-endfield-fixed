# Better Endfield

[English](README.en.md) · [简体中文](README.md)

Better Endfield is an Android LSPosed module for *Arknights: Endfield*, with a settings app and an in-game control panel. Features are implemented by separate modules. Android is the project's only current development and release target. Windows code and build scripts remain in the repository as legacy material; they are outside the current product and acceptance scope. See the [product boundary](docs/PRODUCT_BOUNDARY.md), the [changelog](CHANGELOG.md), and release notes for validation status.

> This is an independently maintained derivative of [Dr-hydra/Better-Endfield](https://github.com/Dr-hydra/Better-Endfield). It is not an official release of the upstream project or the game publisher.

## Platforms and features

| Platform | Main capabilities | Entry point |
| --- | --- | --- |
| Android ARM64 | Models and BEM appearances, character voice, free camera, first person, camera motion, gyroscope and in-game control panel | LSPosed module and companion settings app |

Appearance packages use the standard BEMv1 `.bem` format. See the [BEM creator guide](docs/custom_model/BEM_CREATOR_GUIDE.md) for format and conversion limits.

## Get started

1. Download the APK from the [Android 3.4.1 release](https://github.com/NukumizuKazuhiko/better-endfield-fixed/releases/tag/v3.4.1), and check its compatibility notes and checksum.
2. Install it, enable the module in LSPosed, and select the Endfield package you play.
3. Configure features in the companion app, fully stop the game, then start it again.

See the [Android guide](android/README.md) for setup, compatibility and troubleshooting. Bundled headwear assets target the native Android game at versionCode 61; other client versions need separate verification.

## Build from source

The Android build requires JDK 21, Android SDK 37, NDK 27.2.12479018, CMake 3.22.1, Dobby v1.0.5 and the complete headwear catalog. Follow the [Android build guide](android/README.md#从源码构建); the build fails when that catalog is missing.

## Documentation

| Document | Contents |
| --- | --- |
| [Android guide](android/README.md) | Installation, LSPosed scope, builds and troubleshooting |
| [Technical reference](docs/TECHNICAL_DETAILS.md) | Module boundaries, loaders, configuration, logs and build details (Chinese) |
| [Documentation index](docs/README.md) | Current topic guides and historical records (Chinese) |
| [Changelog](CHANGELOG.md) | Releases and validation boundaries |
| [BEM creator guide](docs/custom_model/BEM_CREATOR_GUIDE.md) | Package authoring and validation |

For bug reports, use [Issues](https://github.com/NukumizuKazuhiko/better-endfield-fixed/issues) and include the platform, game and module versions, and reproduction steps. The Android app can export its runtime log; review it for personal information before sharing.

## Origin and license

The project is licensed under [AGPL-3.0-only](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for upstream and dependency attribution. The project is not affiliated with the game publisher. Back up your configuration before use, and check compatibility after game updates.
