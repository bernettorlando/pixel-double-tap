#!/usr/bin/env bash
set -u

OUT_DIR="emulator-verification"
REPORT="${OUT_DIR}/verification_report.md"
PKG="com.bernettorlando.pixeldoubletap"
MAIN_ACT="${PKG}/.ui.MainActivity"
A11Y_SVC="${PKG}/${PKG}.service.ScreenLockAccessibilityService"
APK_PATH="releases/PixelDoubleTap-v1.0.0.apk"

mkdir -p "${OUT_DIR}"

PASS_COUNT=0
FAIL_COUNT=0

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

record_result() {
  local name="$1"
  local status="$2"
  local details="$3"
  if [ "${status}" = "PASS" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "| ✅ PASS | **${name}** | ${details} |" >> "${REPORT}"
    log "PASS: ${name} -> ${details}"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "| ❌ FAIL | **${name}** | ${details} |" >> "${REPORT}"
    log "FAIL: ${name} -> ${details}"
  fi
}

get_wakefulness() {
  local dump
  dump=$(adb shell dumpsys power | tr -d '\r')
  grep -E "mWakefulness=" <<< "${dump}" | head -n 1 | sed 's/^[[:space:]]*//'
}

get_display_state() {
  local dump
  dump=$(adb shell dumpsys power | tr -d '\r')
  grep -E "mHoldingDisplaySuspendBlocker=" <<< "${dump}" | head -n 1 | sed 's/^[[:space:]]*//'
}

is_screen_awake() {
  local dump
  dump=$(adb shell dumpsys power | tr -d '\r')
  grep -q "mWakefulness=Awake" <<< "${dump}"
}

is_screen_off_or_dozing() {
  local dump
  dump=$(adb shell dumpsys power | tr -d '\r')
  grep -Eq "mWakefulness=(Asleep|Dozing)" <<< "${dump}"
}

capture_png() {
  local filename="$1"
  adb exec-out screencap -p > "${OUT_DIR}/${filename}"
  log "Captured screenshot: ${OUT_DIR}/${filename}"
}

double_tap_at() {
  local x="$1"
  local y="$2"
  adb shell "cat > /data/local/tmp/dt.mks << 'EOF'
type= raw events
count= 10
speed= 1.0
start data >>
Tap(${x},${y},30)
UserWait(120)
Tap(${x},${y},30)
EOF
monkey -f /data/local/tmp/dt.mks 1" >> "${OUT_DIR}/monkey_events.log" 2>&1
}

log "Waiting for Android system services & Pixel Launcher to settle..."
adb wait-for-device
sleep 12

# Wake & dismiss initial boot keyguard / system dialogs
adb shell input keyevent KEYCODE_WAKEUP
sleep 1
adb shell wm dismiss-keyguard || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell settings put secure lock_screen_show_notifications 0 || true
adb shell settings put secure doze_always_on 0 || true
adb shell settings put system screen_off_timeout 600000 || true

SDK_INT=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
RELEASE_VER=$(adb shell getprop ro.build.version.release | tr -d '\r')
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
WM_SIZE=$(adb shell wm size | tr -d '\r' | awk -F': ' '/Physical size/ {print $2}')
WIDTH=$(echo "${WM_SIZE}" | cut -d'x' -f1)
HEIGHT=$(echo "${WM_SIZE}" | cut -d'x' -f2)
HOME_RESOLVE=$(adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r' | tail -n 1)

cat > "${REPORT}" << EOF
# Pixel Double Tap — Live Android Emulator Verification Report

- **Emulator Device**: \`${MODEL}\` (\`${WM_SIZE}\`)
- **Android Version**: Android ${RELEASE_VER} (API ${SDK_INT})
- **Default Home Launcher**: \`${HOME_RESOLVE}\`
- **APK Tested**: \`${APK_PATH}\`

| Result | Test Scenario | Observed Telemetry |
| :--- | :--- | :--- |
EOF

log "Device: ${MODEL}, Android ${RELEASE_VER} (API ${SDK_INT}), Size: ${WM_SIZE}, Launcher: ${HOME_RESOLVE}"

# Prime Pixel Launcher so any first-boot cling/tooltip is cleared
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 2

adb logcat -c

# ---------------------------------------------------------------------------
# 1. Install Signed Release APK & Verify Zero-Internet Privacy Manifest
# ---------------------------------------------------------------------------
log "Installing ${APK_PATH}..."
if adb install -r "${APK_PATH}"; then
  if adb shell dumpsys package "${PKG}" | grep -q "android.permission.INTERNET"; then
    record_result "APK Install & Offline Manifest Check" "FAIL" "Unexpected INTERNET permission found"
  else
    record_result "APK Install & Offline Manifest Check" "PASS" "Signed release APK installed cleanly; zero \`INTERNET\` permission confirmed via \`dumpsys package\`"
  fi
else
  record_result "APK Install & Offline Manifest Check" "FAIL" "adb install failed"
fi

# ---------------------------------------------------------------------------
# 2. Launch App (Initial Sideload Setup Guide State)
# ---------------------------------------------------------------------------
adb shell am start -n "${MAIN_ACT}" >/dev/null
sleep 3
capture_png "01_setup_needed.png"

# ---------------------------------------------------------------------------
# 3. Enable AccessibilityService + Hardware Tap-to-Wake (doze_tap_gesture)
# ---------------------------------------------------------------------------
log "Enabling ScreenLockAccessibilityService and doze_tap_gesture..."
adb shell settings put secure enabled_accessibility_services "${A11Y_SVC}"
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure doze_tap_gesture 1
sleep 3

if adb logcat -d -s PixelDoubleTap:V | grep -q "service_connected"; then
  record_result "AccessibilityService Bind & Hardware Wake Detection" "PASS" "\`ScreenLockAccessibilityService\` bound cleanly (\`service_connected\`); \`doze_tap_gesture=1\` detected live"
else
  record_result "AccessibilityService Bind & Hardware Wake Detection" "FAIL" "Service did not log service_connected within 3s"
fi

# Refresh MainActivity and capture Active Dashboard + Scrolled Controls
adb shell am start -n "${MAIN_ACT}" >/dev/null
sleep 2.5
capture_png "02_active_dashboard.png"

adb shell input swipe $((WIDTH / 2)) $((HEIGHT * 78 / 100)) $((WIDTH / 2)) $((HEIGHT * 22 / 100)) 300
sleep 1.0
adb shell input swipe $((WIDTH / 2)) $((HEIGHT * 78 / 100)) $((WIDTH / 2)) $((HEIGHT * 22 / 100)) 300
sleep 1.5
capture_png "03_zones_and_testpad.png"

# Coordinates in empty middle wallpaper area on Pixel Pro portrait screen
EMPTY_X=$((WIDTH / 2))
EMPTY_Y=$((HEIGHT * 48 / 100))
LOCK_EMPTY_X=$((WIDTH / 2))
LOCK_EMPTY_Y=$((HEIGHT * 62 / 100))

# ---------------------------------------------------------------------------
# 4. Test Single Tap on Empty Home Screen (Must Stay Awake)
# ---------------------------------------------------------------------------
adb shell input keyevent KEYCODE_HOME
sleep 3
capture_png "04_pixel_home_screen.png"

adb shell input tap "${EMPTY_X}" "${EMPTY_Y}"
sleep 1.5
WAKE_SINGLE=$(get_wakefulness)
if is_screen_awake; then
  record_result "Single Tap on Home Screen Ignored" "PASS" "Single tap at (${EMPTY_X}, ${EMPTY_Y}) kept display ON (\`${WAKE_SINGLE}\`)"
else
  record_result "Single Tap on Home Screen Ignored" "FAIL" "Unexpected lock on single tap (\`${WAKE_SINGLE}\`)"
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 1
  adb shell wm dismiss-keyguard || true
fi

# ---------------------------------------------------------------------------
# 5. Test Page Swipe on Home Screen (Must Stay Awake)
# ---------------------------------------------------------------------------
adb shell input swipe $((WIDTH * 80 / 100)) "${EMPTY_Y}" $((WIDTH * 20 / 100)) "${EMPTY_Y}" 250
sleep 0.8
adb shell input swipe $((WIDTH * 20 / 100)) "${EMPTY_Y}" $((WIDTH * 80 / 100)) "${EMPTY_Y}" 250
sleep 1.5
WAKE_SWIPE=$(get_wakefulness)
if is_screen_awake; then
  record_result "Home Screen Page Swipe Ignored" "PASS" "Horizontal launcher swipe kept display ON (\`${WAKE_SWIPE}\`)"
else
  record_result "Home Screen Page Swipe Ignored" "FAIL" "Unexpected lock during swipe (\`${WAKE_SWIPE}\`)"
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 1
  adb shell wm dismiss-keyguard || true
fi

# ---------------------------------------------------------------------------
# 6. Test App Isolation: Double-Tap Inside Android Settings (Must NOT Lock)
# ---------------------------------------------------------------------------
adb shell am start -a android.settings.SETTINGS >/dev/null
sleep 2.5
double_tap_at "${EMPTY_X}" "${EMPTY_Y}"
sleep 1.5
WAKE_SETTINGS=$(get_wakefulness)
DETACH_LOG=$(adb logcat -d -s PixelDoubleTap:I | grep -c "watcher_detached" || true)
if is_screen_awake && [ "${DETACH_LOG}" -ge 1 ]; then
  record_result "In-App Isolation (Overlay Detached in Apps)" "PASS" "Overlay detached on opening Settings (\`watcher_detached=${DETACH_LOG}\`); double-tap kept display ON (\`${WAKE_SETTINGS}\`)"
else
  record_result "In-App Isolation (Overlay Detached in Apps)" "FAIL" "wake=${WAKE_SETTINGS}, watcher_detached=${DETACH_LOG}"
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 1
  adb shell wm dismiss-keyguard || true
fi

# ---------------------------------------------------------------------------
# 7. Test Double-Tap Empty Space on Pixel Launcher Home Screen -> Screen OFF!
# ---------------------------------------------------------------------------
adb shell input keyevent KEYCODE_HOME
sleep 2.8
double_tap_at "${EMPTY_X}" "${EMPTY_Y}"
sleep 2.0
WAKE_HOME_DT=$(get_wakefulness)
DISP_HOME_DT=$(get_display_state)
if is_screen_off_or_dozing; then
  record_result "Double-Tap Empty Home Screen -> Turn Screen OFF" "PASS" "Pixel Launcher double-tap locked screen immediately (\`${WAKE_HOME_DT}\`, \`${DISP_HOME_DT}\`)"
else
  record_result "Double-Tap Empty Home Screen -> Turn Screen OFF" "FAIL" "Screen stayed awake (\`${WAKE_HOME_DT}\`, \`${DISP_HOME_DT}\`)"
fi

# ---------------------------------------------------------------------------
# 8. Test Double-Tap Empty Space on Secure Lock Screen -> Screen OFF!
# ---------------------------------------------------------------------------
adb shell input keyevent KEYCODE_WAKEUP
sleep 1.5
adb shell wm dismiss-keyguard || true
sleep 1.0
adb shell locksettings set-pin 1234
sleep 1.0

# Lock device and wake onto Secure Keyguard (Lock Screen)
adb shell input keyevent KEYCODE_SLEEP
sleep 1.8
adb shell input keyevent KEYCODE_WAKEUP
sleep 2.8
capture_png "05_pixel_lock_screen.png"

double_tap_at "${LOCK_EMPTY_X}" "${LOCK_EMPTY_Y}"
sleep 2.0
WAKE_LOCK_DT=$(get_wakefulness)
DISP_LOCK_DT=$(get_display_state)
if is_screen_off_or_dozing; then
  record_result "Double-Tap Empty Secure Lock Screen -> Turn Screen OFF" "PASS" "Lock Screen double-tap turned display OFF (\`${WAKE_LOCK_DT}\`, \`${DISP_LOCK_DT}\`)"
else
  record_result "Double-Tap Empty Secure Lock Screen -> Turn Screen OFF" "FAIL" "Screen stayed awake on Lock Screen (\`${WAKE_LOCK_DT}\`, \`${DISP_LOCK_DT}\`)"
fi

# ---------------------------------------------------------------------------
# 9. Test PIN Bouncer Protection (Must NOT Lock While Typing PIN) & Unlock
# ---------------------------------------------------------------------------
adb shell input keyevent KEYCODE_WAKEUP
sleep 1.8
# With PIN 1234 set, wm dismiss-keyguard opens the SystemUI PIN Bouncer deterministically
adb shell wm dismiss-keyguard || true
sleep 2.2
capture_png "06_pin_bouncer_protected.png"

double_tap_at "${EMPTY_X}" $((HEIGHT * 30 / 100))
sleep 1.5
WAKE_BOUNCER=$(get_wakefulness)
if is_screen_awake; then
  record_result "PIN Bouncer Protection (No Lock Mid-PIN)" "PASS" "Double-tap while PIN pad visible kept display ON (\`${WAKE_BOUNCER}\`)"
else
  record_result "PIN Bouncer Protection (No Lock Mid-PIN)" "FAIL" "Screen locked while PIN bouncer was open (\`${WAKE_BOUNCER}\`)"
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 1.5
  adb shell wm dismiss-keyguard || true
  sleep 1.5
fi

# Enter PIN 1234 to unlock and clean up credential
adb shell input text 1234
adb shell input keyevent KEYCODE_ENTER
sleep 2.0
adb shell locksettings clear --old 1234 || true

# ---------------------------------------------------------------------------
# 10. Verify Live "Power button presses saved" Counter in MainActivity
# ---------------------------------------------------------------------------
adb shell am start -n "${MAIN_ACT}" >/dev/null
sleep 1.8
adb shell input swipe $((WIDTH / 2)) $((HEIGHT * 25 / 100)) $((WIDTH / 2)) $((HEIGHT * 82 / 100)) 300
sleep 0.8
adb shell input swipe $((WIDTH / 2)) $((HEIGHT * 25 / 100)) $((WIDTH / 2)) $((HEIGHT * 82 / 100)) 300
sleep 1.5
capture_png "07_verified_counter.png"

LOGCAT_ALL=$(adb logcat -d -s PixelDoubleTap:I)
LOCK_SUCCESS_LOGS=$(grep -c "perform_lock_screen success=true" <<< "${LOGCAT_ALL}" || true)
if [ "${LOCK_SUCCESS_LOGS}" -ge 2 ] && grep -q "count=2" <<< "${LOGCAT_ALL}"; then
  record_result "GLOBAL_ACTION_LOCK_SCREEN Execution & Live Counter" "PASS" "\`performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)\` succeeded ${LOCK_SUCCESS_LOGS} times; live counter updated to \`2\`"
else
  record_result "GLOBAL_ACTION_LOCK_SCREEN Execution & Live Counter" "FAIL" "Expected >=2 lock actions and count=2, observed ${LOCK_SUCCESS_LOGS}"
fi

# Dump full PixelDoubleTap & Window/Accessibility diagnostics
adb logcat -d -s PixelDoubleTap:V AndroidRuntime:E > "${OUT_DIR}/logcat_pixeldoubletap.txt"
adb shell dumpsys accessibility > "${OUT_DIR}/dumpsys_accessibility.txt"

cat >> "${REPORT}" << EOF

---
**Summary**: **${PASS_COUNT} Passed**, **${FAIL_COUNT} Failed**
EOF

cat "${REPORT}"
echo "=== PixelDoubleTap Logcat ==="
cat "${OUT_DIR}/logcat_pixeldoubletap.txt"

if [ "${FAIL_COUNT}" -gt 0 ]; then
  exit 1
fi
