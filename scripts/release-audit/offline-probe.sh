#!/usr/bin/env bash
# Android 런타임 캡처 프로브 — /release-audit 런타임 패스가 호출한다.
# 에뮬레이터/기기에서 비행기 모드를 adb로 토글하고, 상태별 스크린샷 + 크래시/ANR 로그를 남긴다.
# 판단(에러페이지·중복 다이얼로그·빈 화면)은 에이전트가 아티팩트를 보고 한다.
#
# 사용법:
#   offline-probe.sh <label> <phase>   # phase: online|offline|recovered
#                                       #  offline   → 비행기 모드 켠 뒤 캡처
#                                       #  recovered → 비행기 모드 끈 뒤 캡처
#   offline-probe.sh --self-check
set -euo pipefail

OUT="${AUDIT_OUT:-$HOME/release-audit-android}"
PKG="com.efreedom.mutter"
mkdir -p "$OUT"

die() { echo "ERROR: $*" >&2; exit 1; }
# 비대화형 셸엔 adb가 PATH에 없을 수 있다 — 프로젝트 SDK 경로로 보강한다.
SDK="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$PATH:$SDK/platform-tools"
command -v adb >/dev/null || die "adb 없음 — $SDK/platform-tools 확인"

device() { adb devices | awk 'NR>1 && $2=="device"{print $1; exit}'; }

if [ "${1:-}" = "--self-check" ]; then
  D=$(device || true)
  echo "OK adb=$(adb version | head -1) device=${D:-none}"
  [ -n "${D:-}" ] || echo "  (연결된 기기 없음 — 에뮬레이터 부팅: mutter-android-conventions '실행·런타임 확인')"
  if [ -n "${D:-}" ]; then
    adb -s "$D" shell pm path "$PKG" >/dev/null 2>&1 && echo "  app=installed" || echo "  app=not-installed (./gradlew :app:installDebug)"
  fi
  exit 0
fi

LABEL="${1:?label 필요}"
PHASE="${2:?phase 필요 (online|offline|recovered)}"
D=$(device || true)
[ -n "$D" ] || die "연결된 기기가 없습니다. 에뮬레이터 부팅 + 대상 화면 표시 후 재실행."

case "$PHASE" in
  offline)   adb -s "$D" shell cmd connectivity airplane-mode enable  || echo "  (airplane-mode 토글 실패 — 수동으로 끌 것)"; sleep 3 ;;
  recovered) adb -s "$D" shell cmd connectivity airplane-mode disable || echo "  (airplane-mode 토글 실패 — 수동으로 켤 것)"; sleep 5 ;;
  online)    ;;
  *) die "phase는 online|offline|recovered" ;;
esac

echo "[$LABEL/$PHASE] 스크린샷 캡처 (device=$D)"
adb -s "$D" exec-out screencap -p > "$OUT/${LABEL}_${PHASE}.png" || echo "  (screencap 실패)"

echo "[$LABEL/$PHASE] 크래시/ANR 로그 수집"
adb -s "$D" logcat -d -b crash > "$OUT/${LABEL}_${PHASE}_crash.txt" 2>/dev/null || true
adb -s "$D" logcat -d -s ActivityManager:E AndroidRuntime:E 2>/dev/null | grep -E "ANR|FATAL" > "$OUT/${LABEL}_${PHASE}_anr.txt" || true
CN=$(grep -c "FATAL EXCEPTION" "$OUT/${LABEL}_${PHASE}_crash.txt" 2>/dev/null || true)
echo "---"
echo "아티팩트: $OUT/${LABEL}_${PHASE}.png"
echo "요약: FATAL ${CN:-0}건"
if [ "$PHASE" = "offline" ]; then
  echo "→ ${LABEL}_offline.png에서 에러페이지 노출 / 다이얼로그 중복(2개↑) / 빈 화면 여부를 에이전트가 판정할 것."
fi
