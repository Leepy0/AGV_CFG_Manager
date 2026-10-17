#!/usr/bin/env bash
# 에뮬레이터 안에서 화면 캡처 투어를 돌리고 PNG 를 build/screenshots 로 가져온다.
# Gradle 의 connectedAndroidTest 는 끝나면 앱을 지워 버려서(캡처 파일도 같이 사라진다) 설치와 실행을 직접 한다.
set -uo pipefail

PKG=kr.agv.cfgmanager
OUT=build/screenshots
mkdir -p "$OUT"

./gradlew :app:installDebug :app:installDebugAndroidTest --console=plain || exit 1

adb shell am instrument -w -e class "$PKG.ScreenshotTourTest" \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument.txt"

# 투어가 중간에 실패해도 그때까지 찍은 화면은 가져온다.
for f in $(adb shell run-as "$PKG" ls files/screenshots 2>/dev/null | tr -d '\r'); do
  adb exec-out run-as "$PKG" cat "files/screenshots/$f" > "$OUT/$f"
done
ls -l "$OUT"

grep -q '^OK (' "$OUT/instrument.txt"
