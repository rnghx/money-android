# Money Android — GitHub-only development and delivery

The user wants no permanent local app projects or APK collections on their PC. This repository is the canonical editable source. Temporary working copies are acceptable for an edit, but remove them after verifying the remote commit and release. Do not recreate Desktop/money or Desktop/apps as permanent workspaces.

- Application ID is permanently `local.money`. Preserve it and all on-device data, bank access, operation edits, category/merchant rules, split allocations, goals and capital history.
- GitHub repo: `rnghx/money-android`; latest manifest: `https://github.com/rnghx/money-android/releases/latest/download/update.json`.
- Existing PC access uses Git Credential Manager (Windows Credential Manager). Never print `git credential fill` output or copy tokens into files. Authenticated Git pushes can reuse the credential helper. GitHub CLI may use a process-only GH_TOKEN supplied from that helper.
- Signing identity is backed up in the repository Actions secret `ANDROID_KEYSTORE_BASE64`. It must never be printed, exported to the repository or regenerated. Expected certificate SHA-256: `ef73f2a3a7c10d308a73caee4423c2c1a3318ddc0775ba6a69a35d68ddb00b0a`.
- Production signing/building happens in GitHub Actions, not on the user's PC. The workflow restores the original keystore only on the ephemeral GitHub runner and removes it afterward.
- For each update, increase integer versionCode and versionName in app/build.gradle; update displayed settings version if present. Commit only intended sources. Push to main; dispatch `.github/workflows/android-release.yml` (workflow_dispatch). The workflow checks logic, builds, runs lint, verifies the certificate, creates a clean source archive and publishes three release assets as one completed release. Never overwrite an existing release.
- Verify workflow success, public latest/update.json and the SHA-256 of the downloadable APK. A successful push alone is not a release. Do not instruct the user to download an APK manually if their installed version already has the updater: Settings → Обновить приложение.
- Never delete/recreate user storage or ask the user to uninstall as an update step. No automatic bank login, network refresh, update check or installer on startup/resume/navigation.
- Tests: `node --test tests/*.test.js`; cloud workflow also executes assembleDebug and lintDebug. Report phone tests honestly.
- Old source archives remain in historical releases. The regular Git tree is the authoritative source for future work.

Shared Android app/update instructions are in docs/ANDROID-APPS.md. Each other app needs its own repo, update manifest and original signing secret. Do not change Chains or other projects from this task.
