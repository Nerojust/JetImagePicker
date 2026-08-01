# Contributing to JetImagePicker

Thanks for considering a contribution!

## Getting started

1. Fork and clone the repo.
2. Open in Android Studio (or run `./gradlew build` from the CLI) — requires JDK 17 and the Android SDK (compileSdk 36).
3. The `app` module is a runnable example that depends on the `JetImagePicker` module's local source (`implementation(project(":JetImagePicker"))`), so changes to the library are immediately reflected when you run `app`.

## Before opening a PR

Run the full local check suite — this is exactly what CI runs:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

- Formatting is enforced by [ktlint](https://github.com/pinterest/ktlint) — run `./gradlew ktlintFormat` to auto-fix.
- Static analysis is enforced by [detekt](https://detekt.dev/). The baseline file is a one-time snapshot taken when detekt was introduced — it is not meant to grow, so don't add new entries to it; fix new findings in your own code instead.
- New logic (a branch, a loop, a parser, anything touching permissions or file I/O) needs a covering unit test.

## Commit / PR conventions

- Keep commits focused — one logical change per commit.
- Write commit messages in the imperative mood (`fix: ...`, `feat: ...`, `docs: ...`) describing *why*, not just *what*.
- Reference the issue number in the PR description if one exists.

## Reporting bugs

Open a GitHub issue with: the `JetImagePickerConfig` you used, the Android version/device, and (if possible) a minimal repro in the `app` module.

## License

By contributing, you agree your contributions are licensed under this project's [MIT License](LICENSE).
