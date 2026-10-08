fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android verify

```sh
[bundle exec] fastlane android verify
```

서비스 계정 키로 Play API 접근 확인(업로드 없음)

### android bundle

```sh
[bundle exec] fastlane android bundle
```

서명된 릴리스 AAB 빌드

### android internal

```sh
[bundle exec] fastlane android internal
```

내부 테스트 트랙 업로드

### android production

```sh
[bundle exec] fastlane android production
```

프로덕션 트랙 업로드 — 업로드 즉시 Google 검토로 들어간다

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
