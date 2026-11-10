# gradle-dep-check

모듈 간 의존성을 분석해 `mutter-android-architecture`의 의존 방향 위반을 탐지한다.

## 절차

1. `settings.gradle.kts`의 `include(...)`로 모듈 목록을 얻는다.
2. 각 모듈 `build.gradle.kts`의 `project(":…")` 의존을 수집한다.
   ```bash
   cd ~/Desktop/Code/Mutter_android
   for f in $(git ls-files '*build.gradle.kts'); do echo "== $f"; grep -o 'project(":[a-z:]*")' "$f"; done
   ```
3. 아래 규칙과 비교하고, 순환 의존을 찾는다.

## 규칙

허용
```
:app → 전부
:feature:* → :domain, :uicomponent, :core:navigation, (:core:audiosync — compose·viewer)
:data → :domain, :core:network
:core:audiosync → :domain
```

금지
```
:feature:* → :feature:*      (feature 간 직접 의존)
:feature:* → :data, :core:network
:domain → 모든 Android 모듈
:uicomponent → :domain, :feature:*
```

## 출력

```
[CRITICAL][위반] :feature:viewer → :feature:inbox — feature 간 직접 의존
[PASS] :domain 순수 JVM
```

위반마다 수정 방향(FeatureRoute·콜백으로 :app에서 연결, 공용 모델은 :domain으로)을 붙인다.
