# MuNote build / signing policy

Updated: 2026-09-29

## Why this file exists

Android only allows an installed application to be updated by an APK signed with a compatible signing key. A CI pipeline that relies on the Android plugin's ephemeral/default debug keystore can therefore produce test APKs that cannot update one another.

MuNote treats test and production identity as two separate channels.

## Test channel

- Application ID: `dev.munote.app.debug`
- Display name: `MuNote Test`
- Build type: `debug`
- Signing key: `app/keystore/munote-test.keystore`
- Certificate SHA-256:
  `29:32:58:F5:97:70:E0:FD:5E:75:F6:49:7E:50:6D:9C:95:DF:CF:82:C7:55:DE:66:DD:76:DE:D5:4A:46:7D:63`

The test key is intentionally committed to this public repository. It is not a secret and must never be used for a production build. Its only purpose is to make MuNote Test APKs produced by local development and GitHub Actions update-compatible with one another.

GitHub Actions supplies monotonically increasing CI version codes through `github.run_number`.

### Invariants

- Do not change the test application ID.
- Do not replace or regenerate the committed test key.
- Do not configure the production application ID to use the test key.
- Do not publish `debug` APKs as production releases.

If the test key ever has to change, treat it as a new test installation channel and document the migration explicitly.

## Production channel

- Application ID: `dev.munote.app`
- Build type: `release`
- Production signing is deliberately not configured in the public repository.
- A future production key must be generated privately and stored outside the repository (for example in an encrypted release environment / secret store).

### Invariants

- Never commit a production `.jks` / `.keystore` file.
- Never print production signing material in CI logs.
- Never replace the production signing key after public distribution without an explicit Android-supported key-rotation plan.
- Production `versionCode` must strictly increase across distributed builds.

## Backup / uninstall migration rule

Normal automatic and manual backups remain inside MuNote's private app directory.

Private in-app backups are removed when Android uninstalls the app. Therefore MuNote also exposes explicit **Export backup** and **Import backup** actions through Android's Storage Access Framework.

Export is user initiated:

MuNote private backup
-> Android system file picker
-> user-selected destination

MuNote does not create a top-level shared-storage folder automatically.

Before uninstalling, changing package channels, factory-resetting, or moving devices, export at least one backup file outside the app-private directory.

## Current migration note

Older CI test APKs before this policy used `dev.munote.app` plus runner-generated debug keys. Their signing keys were ephemeral, so a new fixed-signature APK cannot reliably update them.

The new `dev.munote.app.debug` test channel is intentionally separate and can coexist with such an older installation. Keep the old app installed until any important local data has been migrated.
