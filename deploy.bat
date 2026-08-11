@echo off
echo Building and installing app...
call gradlew installDebug

echo.
echo Enabling Accessibility Service...
adb shell settings put secure enabled_accessibility_services com.flow.shift/com.flow.shift.core.usagetracking.ScrollBlockerAccessibilityService
adb shell settings put secure accessibility_enabled 1

echo.
echo Launching App...
adb shell am start -n com.flow.shift/.MainActivity

echo.
echo Done!
