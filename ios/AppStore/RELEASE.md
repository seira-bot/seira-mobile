# TestFlight and App Store release

The app is native Swift and uses XcodeGen; EAS commands do not apply.

## One-time Apple setup

1. Enrol in the Apple Developer Program.
2. Register the bundle IDs `bot.seira.app`, `bot.seira.app.widgets`, and `bot.seira.app.share` (or change them in `project.yml` before the first upload).
3. Register the App Group `group.bot.seira.shared`. Enable App Groups and Keychain Sharing for the app and Share extension identifiers, then add the group to both. Keep the app's legacy `$(AppIdentifierPrefix)bot.seira.app` Keychain group during upgrades so existing pairings can migrate safely.
4. Create the matching app in App Store Connect with a unique SKU. The listing name is in `en-US/name.txt`. The primary category is **Productivity**.
5. Create or select Apple Distribution certificates and App Store provisioning profiles for the containing app and both extensions.
6. Add the review contact details in App Store Connect; do not commit private contact data or App Store Connect keys.

## Before every upload

1. Run `swift test` from `ios/` and the repository test suite.
2. Generate the Xcode project with `xcodegen generate` from `ios/`.
3. Set `DEVELOPMENT_TEAM` for the Release configuration in Xcode or on the archive command line.
4. Increment `CURRENT_PROJECT_VERSION` for every upload. Update `MARKETING_VERSION` only for a new App Store version.
5. Archive a generic iOS device build and validate it in Xcode Organizer.
6. Upload to App Store Connect and distribute to internal TestFlight testers first.
7. Complete a real-iPhone pass for pairing, Bonjour permission, Keychain restore and upgrade migration, Tailscale, optional hosted HTTPS, approvals, secure credential entry through Apple Passwords and by paste, background/foreground reconciliation, sign-out/revocation, transcript sharing, and Share-sheet delivery of text, a link, an image, and a document. Repeat Share-sheet delivery after force-quitting the containing app.
8. After internal testing, submit to an external TestFlight group before App Review.

## App Store Connect

The listing text and screenshots live in this folder. `tools/asc-listing.mjs` checks them and uploads them with the App Store Connect API. It changes only the version in Prepare for Submission. It never submits and never releases.

| Item | Source |
| --- | --- |
| Name, subtitle, description, keywords, promotional text, URLs | `en-US/*.txt`, `id/*.txt` (App Store Connect calls Indonesian `id`) |
| What's New | `<locale>/release_notes.txt`. App Store Connect refuses it on the first version. `asc-release.mjs submit` fills it on an update before it submits. |
| Screenshots | `frames/<locale>/iphone-6.9/` and `frames/<locale>/ipad-13/`. See `README.md`. |
| App Privacy | `privacy-answers.md`. Verify it against the binary. |
| Review notes | `review-notes.md`. The review server address and passphrase stay in App Store Connect only. |
| Support URL | `https://github.com/seira-bot/seira/issues` |
| Privacy policy URL | `https://seira.bot/privacy` |
| Category | Productivity |

```bash
node ios/AppStore/tools/asc-listing.mjs check
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=... ASC_APP_ID=... node ios/AppStore/tools/asc-listing.mjs text
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=... ASC_APP_ID=... node ios/AppStore/tools/asc-listing.mjs screenshots --replace
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=... ASC_APP_ID=... node ios/AppStore/tools/asc-listing.mjs status
```

- Add a real review contact in App Store Connect. Do not commit private contact data or App Store Connect keys.
- Choose manual release for 1.0. Enable a phased release after the first production build is stable.

The unsigned simulator CI proves compilation, not distribution signing. Use the workflow below to upload a signed build.

## Upload from CI (no Mac needed)

The workflow `ios-testflight.yml` archives the app on a GitHub macOS runner. It signs the app and uploads it to TestFlight.

### One-time setup

1. Finish the Apple setup above. The App Store Connect app record must exist before the first upload.
2. Create the GitHub environment `app-store`. Set its deployment branch policy to `main` only.
3. Create an App Store Connect API key with the Admin role. Automatic signing needs this role to create the cloud-managed distribution certificate.
4. Download the `.p8` file once. Apple does not show it again.
5. Set the four secrets in the environment. Encode the `.p8` file as base64 first.

```bash
gh secret set ASC_KEY_P8_BASE64 --env app-store --repo seira-bot/seira-mobile < <(base64 < AuthKey_XXXX.p8 | tr -d '\n')
gh secret set ASC_KEY_ID --env app-store --repo seira-bot/seira-mobile
gh secret set ASC_ISSUER_ID --env app-store --repo seira-bot/seira-mobile
gh secret set APPLE_TEAM_ID --env app-store --repo seira-bot/seira-mobile
```

### Run it

```bash
gh workflow run ios-testflight.yml --repo seira-bot/seira-mobile -f ref=main
```

- With all four secrets set, the workflow signs the app and uploads it.
- With no secret set, the workflow makes an unsigned archive. It uploads nothing and shows a NOT UPLOADED warning.
- With only some secrets set, the workflow stops and names the missing secrets.
- The run uses the `xcode-27` runner image. From April 2027 App Store Connect rejects uploads built with an iOS SDK older than 27.

### Build number

- The workflow uses the input `build_number` when you set it.
- Otherwise it uses `<CURRENT_PROJECT_VERSION in project.yml>.<run number>`, for example `6.14`.
- The app and both extensions get the same build number.
- The app version comes from `MARKETING_VERSION` in `project.yml`.

### After the upload

App Store Connect processes the build for several minutes. Testers then install the build from TestFlight on a real iPhone. Run the real-iPhone pass from the list above.

## Automatic release

The workflow `apple-release.yml` builds the iOS app and the macOS app (Mac Catalyst, same bundle ID).
It uploads both to App Store Connect. Then it puts each build on its App Store version and sends the version to App Review.
The script `tools/asc-release.mjs` does the App Store Connect steps. It needs the secrets of the environment `app-store`.

| Start | What happens |
| --- | --- |
| Push a tag `ios-vX.Y.Z` | Build, upload and submit both apps. The tag must equal `MARKETING_VERSION` in `ios/project.yml`. If it differs, the run fails before the build. |
| Run by hand | Choose `platforms` (`both`, `ios`, `macos`) and `submit`. |
| Every day at 01:17 UTC | Send the builds of the newest `ios-v*` tag that are not yet in review or live. |

### Release a version

1. Set `MARKETING_VERSION` in `ios/project.yml` and merge it to `main`.
2. Push the tag: `git tag ios-vX.Y.Z && git push origin ios-vX.Y.Z`. Use the same version as in step 1.
3. Wait for the run. The submit job waits up to 90 minutes for App Store Connect to process each build.
4. Check the result in the run summary. Each platform shows one of these results:
   - `submitted`: the version is in App Review. It releases after approval.
   - `skipped`: another version of that platform is already in review or waits for release.
   - `done`: the version is already live or processing.

The build number is `<CURRENT_PROJECT_VERSION in project.yml>.<run number>`. iOS and macOS use the same number.

### What the script decides

- It skips a platform that has a version in review. The next daily run tries again.
- It renames the editable version to the new version, or creates one.
- It copies the review details from the newest iOS version when the macOS version has none.
- It does not submit when the listing is not complete. Each locale needs a description, keywords, a support URL and the screenshots of the platform. iOS needs iPhone 6.7" and iPad 13". macOS needs `APP_DESKTOP`.
- It never prints review details.

Preview the decisions without a change:

```bash
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=... ASC_APP_ID=... node ios/AppStore/tools/asc-release.mjs plan --version X.Y.Z
```

### Stop the submission

- Set the repository variable `APPLE_AUTO_SUBMIT` to `false`. The workflow uploads the builds and sends nothing to App Review.
- For one run, start the workflow by hand and turn `submit` off. This uploads to TestFlight only.

```bash
gh workflow run apple-release.yml --repo seira-bot/seira-mobile -f platforms=both -f submit=false
```

### Optional secrets for the Mac installer

The macOS upload needs a Mac Installer Distribution certificate. By default Xcode uses the cloud-managed certificate.
If the macOS export fails on the installer signature, make that certificate and add it to the environment `app-store`:

- `MAC_INSTALLER_P12_BASE64`: the `.p12` file, encoded with base64.
- `MAC_INSTALLER_P12_PASSWORD`: the password of the `.p12` file.

Set both or neither. The job puts the certificate in a temporary keychain and deletes the keychain at the end.

### Failed runs

- If one build fails, the other build stays uploaded. The daily run then submits the platform that has a valid build.
- To build one platform again, start the workflow by hand and choose that platform. The log of a failed build is in the artifact `xcodebuild-logs-<platform>`.
- The environment `app-store` accepts the branch `main` and the tags `ios-v*` only.

### macOS listing

The macOS version has its own text and screenshots in App Store Connect.

```bash
node ios/AppStore/tools/asc-listing.mjs text --platform MAC_OS
node ios/AppStore/tools/asc-listing.mjs screenshots --platform MAC_OS --replace
node ios/AppStore/tools/asc-release.mjs review-detail --platform MAC_OS
node ios/AppStore/tools/asc-listing.mjs status --platform MAC_OS
```

App Store Connect refuses "What's New" on the first version of a platform. The script skips it for that version.
