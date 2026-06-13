# Self-update and release pipeline

This document covers the end-to-end flow for shipping a new Miyo release: how
the app finds an update, how it verifies one, how the GitHub Actions workflow
builds and publishes the artifact, and the things only a human can do (Pages
setup, secret rotation, rollback).

If you are looking for the "how do I ship a new version" recipe, jump to
[Cutting a release](#cutting-a-release).

---

## Architecture and data flow

```
       ┌────────────────────┐       ┌────────────────────────────┐
       │  SaikouApp          │       │  Logger (SentryLogger)     │
       │  (Compose root)     │       └──────────────▲─────────────┘
       └────────┬───────────┘                       │
                │ koinViewModel + lifecycle hooks   │
                ▼                                   │
       ┌────────────────────┐                       │
       │  UpdateViewModel    │  (singleton via Koin) │
       │  :presentation      ├──┐                    │
       └────────┬───────────┘  │                    │
                │              │ logger.report*()   │
                ▼              └──────►──────────────┘
       ┌────────────────────────────────────────────┐
       │ Use cases (:domain/usecase/update)          │
       │   CheckForUpdateUseCase                     │
       │   DownloadUpdateUseCase                     │
       │   InstallUpdateUseCase                      │
       │   CleanupUpdateArtifactsUseCase             │
       │   + UpdateDecisionEngine (pure)             │
       └────────┬───────────────────────────────────┘
                │
                ▼
       ┌────────────────────────────────────────────┐
       │  UpdateRepository (:domain interface)       │
       └────────┬───────────────────────────────────┘
                │
                ▼
       ┌────────────────────────────────────────────┐
       │  UpdateRepositoryImpl (:data-android)       │
       │   • Ktor HttpClient (shared, OkHttp engine) │
       │   • Sha256Streamer (file-streamed digest)   │
       │   • ApkValidator + ObservedApkInfo          │
       │   • SignerFingerprinter (PackageManager)    │
       │   • UpdateCache (cacheDir/updates/)         │
       │   • InstallerLauncher (FileProvider intent) │
       │   • UnknownSourcesGuard (settings deep link)│
       └────────────────────────────────────────────┘
```

The check fires **once per process** when the app first has network access
(driven from `SaikouApp`'s connectivity flow). A failed check **never blocks
the app** — the dialog stays hidden and the user keeps using Miyo.

Compose dialog and the rest of the UI:
* The dialog lives in `:shared-ui/androidMain` (`AppUpdateDialog`).
* Hosted at the root `Box` in `SaikouApp.kt` so it overlays every nav destination.
* Mandatory updates use `dismissOnBackPress = false` and `dismissOnClickOutside = false`.

Use case shapes follow the existing repo convention (single `suspend operator
fun invoke()`). Decision logic lives entirely in `UpdateDecisionEngine` — pure
Kotlin, exhaustively unit-tested in `:domain`.

---

## Manifest schema

The remote `update.json` is fetched as plain JSON via Ktor. The wire DTO is
declared in `:data-android` as `UpdateManifestDto` and mapped to the
plain-Kotlin `UpdateManifest` in `:domain`. Unknown fields are ignored at
the JSON-config level (`ignoreUnknownKeys = true`), so the server can add
new keys without breaking shipped clients.

```jsonc
{
  "versionCode": 5,                          // integer, mandatory
  "versionName": "1.3.0",                    // display string, mandatory
  "minimumSupportedVersion": 1,              // integer, mandatory
  "apkUrl": "https://github.com/.../miyo-v1.3.0.apk", // mandatory
  "sha256": "lowercase 64-hex digest",       // mandatory, lowercased on parse
  "releaseNotes": "Bug fixes…",              // string, mandatory (may be "")
  "mandatory": false,                        // optional, defaults to false
  "publishedAt": "2026-06-13T10:00:00Z"      // optional, ISO-8601
}
```

`versionCode` is the **only** field used for ordering. `versionName` is purely
cosmetic — string comparison never decides whether to offer the update.

---

## GitHub secrets

Add these under **Settings → Secrets and variables → Actions → New repository
secret**:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded JKS keystore. Generate locally with `base64 -i ~/.android/debug.keystore -o keystore.b64`, then paste the *contents* of `keystore.b64`. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password (debug keystore default: `android`). |
| `ANDROID_KEY_ALIAS` | Key alias (debug keystore default: `androiddebugkey`). |
| `ANDROID_KEY_PASSWORD` | Key password (debug keystore default: `android`). |

Optional:

| Secret | Purpose |
| --- | --- |
| `SENTRY_AUTH_TOKEN` | Enables source-context upload to Sentry during the release build. Without it, the build still succeeds (source bundle skipped). |

The workflow's **Verify release signing secrets are present** step fails fast
when any of the four required secrets is missing.

---

## GitHub Pages setup (one-time)

1. Repository → **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Save. (No branch selection needed — the workflow uses `actions/upload-pages-artifact`
   and `actions/deploy-pages`, which require the Pages source to be "Actions".)

After the first successful release, `update.json` is reachable at:

```
https://theshid.github.io/miyo/update.json
```

This URL is wired into `BuildConfig.UPDATE_MANIFEST_URL` via the
`buildConfigField` block in `app/build.gradle.kts`. To point a build at a
different host (e.g. a staging Pages site), set `UPDATE_MANIFEST_URL` in
`local.properties` or as an environment variable before building.

---

## Cutting a release

1. Decide the new version. Set both `versionCode` and `versionName` in
   `app/build.gradle.kts`:
   ```kotlin
   versionCode = 5
   versionName = "1.3.0"
   ```
2. Commit the bump on `main`:
   ```bash
   git commit -am "Bump to versionCode 5 / versionName 1.3.0"
   git push origin main
   ```
3. Tag the commit and push the tag:
   ```bash
   git tag v1.3.0
   git push origin v1.3.0
   ```
4. The `release` workflow runs automatically. It will:
   - validate the tag matches `versionName` (and surface `versionCode`),
   - run ktlint, detekt, and the unit tests across `:domain`, `:data-android`, and `:app`,
   - sign-and-build `app:assembleRelease`,
   - rename the artifact to `miyo-v1.3.0.apk`,
   - compute its lowercase SHA-256,
   - create / un-draft a GitHub Release and upload the APK,
   - emit `update.json` via `jq` and publish to GitHub Pages.
5. Once the Pages deploy completes, sanity-check from a fresh device:
   ```bash
   curl -fsSL https://theshid.github.io/miyo/update.json | jq
   ```

Manual re-run: trigger the workflow via **Actions → release → Run workflow**
with the existing tag (e.g. `v1.3.0`) in the `tag` input. The workflow is
idempotent — it reuses an existing release and re-uploads the asset with
`--clobber`.

---

## Signing-key continuity (CRITICAL)

> **Android refuses to install an update signed by a different certificate
> than the one already installed.** If you change the signing key, every
> existing user has to uninstall the app and reinstall the new one from scratch.

* Historical Miyo APKs (≤ 1.2.x) were signed with the local Android SDK debug
  keystore (`~/.android/debug.keystore`, alias `androiddebugkey`, password
  `android`). The certificate's SHA-256 fingerprint is
  `eef1d3955075cd26fc8fd2c4d6c66ac27aa5cb1f822f97b214be6adf0de66e5a`.
* To preserve update continuity for everyone on 1.2.x, the GitHub secrets MUST
  encode that exact keystore. Verify before uploading by extracting the
  fingerprint:
  ```bash
  keytool -printcert -jarfile ~/path/to/miyo.apk | grep "SHA256:"
  ```
* The CI build never generates a new signing key. The workflow base64-decodes
  the supplied secret into `app/build/ci-keystore.jks` and uses it for the
  release signing config.
* If at some point you decide a key rotation is necessary, plan a coordinated
  re-onboarding: ship a final 1.2.x release that informs users they must
  uninstall, then publish 1.3.0 under the new key.

---

## Rollback

If a release ships broken:

1. **Stop the bleeding.** From the release workflow you can re-run with the
   previous tag (e.g. `v1.2.2`). The workflow re-computes the manifest with
   the older versionCode, which means new clients will see "up to date" (their
   installed code already matches). The `workflow_dispatch` path skips the
   "versionCode must advance" check precisely because rollback INTENDS to go
   backward.
2. Alternatively, hand-edit the Pages output:
   - Run the workflow against the prior tag, OR
   - check out the prior tag locally, run the `Generate update.json with jq` step
     by hand, and push to the `gh-pages` branch if your Pages source is set
     to a branch. (With the default "Actions" source, prefer the workflow re-run.)
3. Optionally delete the bad release from the GitHub UI to stop direct
   downloads. The `update.json` is what the in-app updater consults — direct
   downloads from a release page are a non-canonical channel.
4. If users have already installed the broken build, the next release with a
   higher versionCode will roll them forward as soon as it ships.

> ⚠️ **Pre-feature tags cannot be re-built via this workflow.** Any tag
> created before this `release.yml` + signing block was added (e.g. the
> `v1.0.x` / `v1.1.x` / `v1.2.x` family) checks out an `app/build.gradle.kts`
> that does NOT consume the signing environment variables; `assembleRelease`
> would produce an unsigned APK and the staging step would reject it. To
> roll back to such a version, either rebuild + sign locally with the
> `miyo-release` helper, or cherry-pick the signing block onto the older
> tag's branch first.

---

## Forcing a mandatory update

Two ways to make `1.3.0` mandatory for everyone:

1. **Per-release flag.** Add `"mandatory": true` to the generated manifest.
   The workflow currently writes `false` by default; flip it in `release.yml`
   for the one-off release, or extend the workflow with an input dispatch
   parameter.
2. **Compatibility floor.** Bump `minimumSupportedVersion` to the new
   `versionCode`. Any client whose installed code is strictly below the
   floor will see the update as mandatory regardless of the `mandatory` flag.
   This is the right lever when a server-side change makes older clients
   non-functional.

The app's decision is encoded in `UpdateDecisionEngine.evaluate()`:

```kotlin
val mandatory = manifest.mandatory ||
    installed.versionCode < manifest.minimumSupportedVersion
```

---

## Security verification performed by the app

Before any APK is handed to the system installer, the app checks:

1. **SHA-256 checksum** of the downloaded file matches `manifest.sha256`
   (lowercase hex, streamed via `MessageDigest` in 8 KiB chunks).
2. **Package name** equals the running app's package
   (`ani.saikou.v2`). Repackaged "lookalike" APKs are rejected.
3. **versionCode** of the downloaded APK equals `manifest.versionCode`.
   Catches a swapped or out-of-sync release asset.
4. **Signing certificate** SHA-256 of the downloaded APK equals the
   currently-installed app's signing certificate. Uses
   `PackageManager.GET_SIGNING_CERTIFICATES` on API 28+ and the deprecated
   `GET_SIGNATURES` path on API 26-27.

Any failure surfaces a structured `UpdateError` (`ChecksumMismatch`,
`PackageMismatch`, `VersionMismatch`, `SignerMismatch`) in the dialog and
deletes the partial / invalid APK. The system installer is **never** invoked
on an unverified file.

---

## Known Android limitation: user must confirm installation

There is **no silent self-install** for off-Play distribution. The app:

* requests `android.permission.REQUEST_INSTALL_PACKAGES` at install time,
* checks `PackageManager.canRequestPackageInstalls()` before launching,
* deep-links to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package
  if the user hasn't granted the per-app toggle yet,
* hands the verified APK to the system installer via `ACTION_VIEW` with a
  `FileProvider` content URI and `FLAG_GRANT_READ_URI_PERMISSION`.

The user then sees Android's stock "Update Miyo?" confirmation screen and
must tap **Install**. Mandatory updates are non-dismissible **inside the
dialog**, but the user can still press the home button — this is a platform
behavior we cannot override and do not try to.
