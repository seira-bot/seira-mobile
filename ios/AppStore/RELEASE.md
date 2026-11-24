# TestFlight and App Store release

The app is native Swift and uses XcodeGen; EAS commands do not apply.

## One-time Apple setup

1. Enrol in the Apple Developer Program.
2. Register the bundle IDs `bot.seira.app`, `bot.seira.app.widgets`, and `bot.seira.app.share` (or change them in `project.yml` before the first upload).
3. Register the App Group `group.bot.seira.shared`. Enable App Groups and Keychain Sharing for the app and Share extension identifiers, then add the group to both. Keep the app's legacy `$(AppIdentifierPrefix)bot.seira.app` Keychain group during upgrades so existing pairings can migrate safely.
4. Create the matching app in App Store Connect with the name **Seira**, primary category **Productivity**, and a unique SKU.
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

- Copy the localized text from `en-US/`.
- Use `privacy-answers.md` and verify it still matches the binary.
- Use `review-notes.md`, adding a real review contact in App Store Connect.
- Support URL: `https://github.com/seira-bot/seira/issues`
- Privacy policy URL: TBD: Seira privacy policy URL (must be public before store submission)
- Choose manual release for 1.0; enable a phased release after the first production build is stable.

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
- The run uses Xcode 26 or newer. App Store Connect rejects uploads from older versions.

### Build number

- The workflow uses the input `build_number` when you set it.
- Otherwise it uses `<CURRENT_PROJECT_VERSION in project.yml>.<run number>`, for example `6.14`.
- The app and both extensions get the same build number.
- The app version comes from `MARKETING_VERSION` in `project.yml`.

### After the upload

App Store Connect processes the build for several minutes. Testers then install the build from TestFlight on a real iPhone. Run the real-iPhone pass from the list above.
