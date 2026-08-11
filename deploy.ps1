Write-Host "Building and installing app..."
.\gradlew installDebug

Write-Host "`nEnabling Accessibility Service..."
adb shell settings put secure enabled_accessibility_services com.flow.shift/com.flow.shift.core.usagetracking.ScrollBlockerAccessibilityService
adb shell settings put secure accessibility_enabled 1

Write-Host "`nLaunching App..."
adb shell am start -n com.flow.shift/.MainActivity

Write-Host "`nDone!"
