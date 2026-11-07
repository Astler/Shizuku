# PressF Access

An independent Android privileged-access manager maintained in Astler/Shizuku,
based on Shizuku by RikkaApps and thedjchi's 13.7.0 fork. It lets compatible apps
use system APIs through a server started with ADB or root. This project is not
affiliated with or endorsed by RikkaApps or the other upstream authors.

The working name and package are placeholders. Change `FORK_APP_NAME` and
`FORK_APPLICATION_ID` in `gradle.properties` before choosing a final identity.
`FORK_PERMISSION_PREFIX` is derived as `<applicationId>.permission` in the root
Gradle file. The Java/Kotlin namespaces, Binder descriptors, transaction codes,
client provider suffixes and Shizuku-API discovery identifiers remain unchanged.

Compared with its sources, this fork includes the F1 Android 17 compatibility,
ADB cancellation, package lookup and permission-revocation fixes; a separate app
identity and neutral shield artwork; and conservative cleanup of user services
left behind by a previous server instance. It retains thedjchi's wireless ADB,
boot startup, watchdog, TCP and stealth features. Its update checker uses this
fork's GitHub releases, not upstream APKs.

## Building

Clone with submodules, then build with JDK 21 and the Android SDK:

```sh
git clone --recurse-submodules https://github.com/Astler/Shizuku.git
cd Shizuku
./gradlew :manager:assembleGithubDebug
./gradlew :manager:assembleGithubRelease :manager:assemblePlayRelease :manager:bundlePlayRelease
./gradlew test
```

The project currently selects compile/target SDK 37, Build Tools 37.0.0,
NDK 29.0.13113456 and CMake 3.31 or newer. Set `sdk.dir` in local.properties to a
writable SDK installation. With AGP 8.11.1, an SDK distributed as `android-37.0`
may require a local Gradle init script selecting that platform hash (see the F2
verification report). This selects the same API 37 platform, not an older SDK.

The `distribution` dimension has two flavors with the same application ID and version:

- `github` retains self-updates from GitHub, stealth/stub APKs, accessibility pairing,
  battery exemption requests and the opt-in watchdog. GitHub Actions publishes this flavor.
- `play` uses notification pairing, omits the updater/stealth/accessibility/watchdog
  implementations and their UI, and opens the general battery optimization settings.
  Previously saved feature preferences cannot reactivate removed services.

Both flavors include Settings → Privacy policy. Neither changes the client/server protocol.

Release outputs (with the default identity/version):

- `manager/build/outputs/apk/github/release/pressf.gg.access-v1.0.0-release.apk`
- `manager/build/outputs/apk/play/release/pressf.gg.access-v1.0.0-play-release.apk`
- `manager/build/outputs/bundle/playRelease/manager-play-release.aab`

Debug APKs use the corresponding `apk/<flavor>/debug/` directory and `-debug.apk`
suffix. Release mapping copies in `out/mapping/` include the flavor to avoid collisions.
Use your own signing configuration for distribution; see `signing.gradle`.

To inspect final APKs without interacting with a device:

```sh
python3 tools/verify-distributions.py --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" \
  --github-apk manager/build/outputs/apk/github/release/pressf.gg.access-v1.0.0-release.apk \
  --play-apk manager/build/outputs/apk/play/release/pressf.gg.access-v1.0.0-play-release.apk \
  --output out/distribution-verification
```

Optionally pass `--baseline-permissions <pre-flavor-aapt2-dump.txt>` to assert that
GitHub's full permission list is unchanged. Play retains `FOREGROUND_SERVICE` for
notification pairing's `shortService`; the special-use watchdog service is absent.
Android 7 / API 24 or later is required. Wireless debugging generally requires
Android 11 or later; root or a PC ADB connection are alternatives.

UI strings in `manager/src/main/res` use `@FORK_APP_NAME@` templates, including
translated strings. `generateForkResources` substitutes the configured name into
the resources compiled into the APK. Runtime labels, About, notifications and
permission prompts therefore use the same identity. NOTICE and LICENSE are
packaged as assets and the About dialog displays NOTICE.

## Client compatibility and authorization

Existing Shizuku-API clients, including `dev.astler.inveditormc`, still request
`moe.shizuku.manager.permission.API_V23`. The server continues to discover and
list apps by that requested permission and the existing client metadata. This
fork **does not declare** that permission or any permission in the upstream
manager namespace. Its own declarations use `<applicationId>.permission.*`.

On a device without a separate app declaring the legacy permission, Android
runtime grant/revoke operations may fail. They are best effort: the server
records the user's decision and updates live clients independently, catches
framework failures and completes permission replies. Saved config decisions take
precedence on later server starts. An Android permission check of the old marker
can remain denied even while `Shizuku.checkSelfPermission()` reports access via
the server. Clients should use the API's Binder availability and permission
methods; detecting installation by a fixed package or permission owner is not
compatible with a renamed manager. No upstream-package stub is installed by
this build. Stealth mode, if explicitly used, creates a stub for the configured
fork package, and the existing APK-path lookup locates the renamed manager.

## User services after server restart

Before spawning an app_process user service, the server writes a private launch
ticket under `/data/local/tmp/<managerPackage>-user-services-<serverUid>` (0700).
The starter records its PID, process start ticks and expected process name
before loading client code. Tickets also record the boot ID and originating
server PID/start ticks. Before accepting clients, a new server cleans up only
journaled app_process processes with matching UID, boot, PID/start ticks and name,
after verifying that their originating server instance is gone. It rechecks the
identity before signalling. Root and shell stores are separate. Unreadable or
ambiguous identities are skipped; a new unjournaled service launch is refused.

This deliberately leaves services from older, unjournaled builds alone. A name
such as `dev.astler.inveditormc:world_io` is insufficient proof of ownership.
Changing root/ADB modes, SELinux restrictions or client code that changes its
process name can prevent cleanup; these cases never justify killing an unrelated
process. Rebooting clears pre-upgrade processes. The identification rules have
unit coverage; restart and World Editor integration testing remain reviewer work.

## Attribution and license

Based on [Shizuku by RikkaApps](https://github.com/RikkaApps/Shizuku), with
modifications from [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku),
[SorMaze/Shizuku](https://github.com/SorMaze/Shizuku),
[HmnDev-Tech/shevery](https://github.com/HmnDev-Tech/shevery), and a credited F1
port from [kerneldroid/Nightzuku](https://github.com/kerneldroid/Nightzuku).
See [NOTICE](NOTICE), individual commit attribution and [LICENSE](LICENSE).
The code is licensed under Apache-2.0. Upstream trademarks and artwork are not
used as this modified app's identity; the shield is a temporary neutral asset.

The source README documents the
[upstream branding restrictions](https://github.com/RikkaApps/Shizuku#license).
Rebranding alone does not establish eligibility for any app store; final name,
application ID, icon, signing and release configuration belong to the owner.
