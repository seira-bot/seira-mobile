# Release notes for Google Play

The publish workflow reads the notes of a release from this folder.

Folder format:

```
release-notes/<versionName>/<language>.txt
```

- `<versionName>` is the `appVersionName` in `android/app/build.gradle.kts`, for example `1.2.0`.
- `<language>` is a Play language code, for example `en-US` or `id`.
- Each file holds the plain text of the notes for that language.
- A file must not be empty and must not exceed 500 characters.
- The `production` track needs the folder of the version. Other tracks can go without it.
