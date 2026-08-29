# Privacy Policy for FlowShift – Stop Scrolling

**Effective Date:** July 6, 2026

> **Summary:** FlowShift – Stop Scrolling is a completely offline app that does **not collect, share, or transmit** any personal information, app usage statistics, or camera data to any servers. All features—including app blocking and camera-based exercises—operate 100% locally on your device. We don't even have internet permissions.

## 1. Introduction

This Privacy Policy explains how **FlowShift – Stop Scrolling** ("we", "our", or "the App") handles information when you use our Android application. We are committed to protecting your privacy and providing full transparency regarding our data practices.

By using the App, you agree to the practices described in this Privacy Policy.

## 2. Information We Collect

**We do not collect any personal information.** The App operates entirely offline and does not require an account, login, or any personal details to function. Specifically:

- We do **not** collect your name, email address, phone number, or any personally identifiable information.
- We do **not** collect location data.
- We do **not** use any analytics trackers or advertising networks (there are no ads in the App).
- The App does **not** have the Android Internet permission (`android.permission.INTERNET`), meaning it is physically impossible for the App to send your data anywhere.

## 3. Data Stored Locally on Your Device

To provide its core functionality, the App processes and stores certain data **locally on your device only**:

- **App Usage Data:** The App uses the Package Usage Stats permission to monitor which apps you are using and for how long. This data is strictly used to enforce the time limits and blocks you have configured. It is processed in real-time and stored locally.
- **App Settings & Limits:** Your configured screen time limits, blocked app lists, and preferences are stored securely on your device.
- **Camera Data:** During exercise challenges, the App uses your camera to track your movements. These camera frames are processed in real-time in the device's memory and are **never saved, recorded, or transmitted.**

This data is stored using Android's Room database and DataStore Preferences. It remains entirely on your device and is **never transmitted** to any server or third party. If you uninstall the App or clear its data, all locally stored data is permanently deleted.

## 4. Camera Usage & Exercise Tracking

The App requests **camera permission** to facilitate physical exercise challenges designed to help you stop scrolling and regain focus.

- The camera is used for real-time motion and pose tracking using on-device machine learning (Google MediaPipe).
- The video frames are analyzed **live in memory** solely to count your repetitions or verify your exercises.
- No video or images are ever captured, saved to your gallery, or transmitted over the internet.
- All machine learning processing happens **entirely on your device**, without connecting to external servers.

## 5. Permissions Explained

The App requests the following Android permissions, all of which are strictly required for its core app-blocking and exercise features:

- **Usage Access (`PACKAGE_USAGE_STATS`):** Required to monitor which apps are currently running so the App can enforce your time limits and block distracting apps.
- **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):** Required to draw the blocking screen over distracting apps when your set time limits are reached or an exercise challenge is triggered.
- **Camera (`CAMERA`):** Required for the exercise tracking challenges to verify your physical movements using on-device vision models.
- **Foreground Service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`):** Required to keep the time-tracking service running reliably in the background so your app limits are accurately enforced even when FlowShift is closed.
- **Post Notifications (`POST_NOTIFICATIONS`):** Required to display status notifications for the background tracking service, keeping you informed that FlowShift is actively monitoring your usage.
- **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`):** Required to detect and block infinite-scroll feeds (such as Instagram Reels and YouTube Shorts) within apps, enabling fine-grained content blocking beyond simple app-level restrictions.
- **Query All Packages (`QUERY_ALL_PACKAGES`):** Required to discover and list all installed apps on your device so you can select which apps to restrict.
- **Device Admin (`BIND_DEVICE_ADMIN`):** Optionally used to provide stronger enforcement of app blocking that cannot be easily bypassed.
- **Advertising ID (`AD_ID`):** Declared for Google Play policy compliance. The App does not use this permission to serve ads or track users, and no data is transmitted since the App lacks internet access.

## 6. Third-Party Services

The App utilizes **Google MediaPipe Tasks Vision** for on-device pose detection and movement tracking during exercise challenges. This machine learning library processes data **entirely locally on your device** and does not send any camera feeds, images, or telemetry to Google or any external servers.

## 7. In-App Purchases

The App offers optional premium features available through **Google Play's in-app billing system**. All purchase transactions are handled entirely by Google Play—the App itself does not process, store, or have access to any payment information. Google Play's own privacy policy governs how your payment data is handled.

## 8. Data Sharing

Because the App operates 100% offline and lacks internet access permissions, we do **not share, sell, or transmit** any of your data to third parties, analytics providers, or advertising networks.

## 9. Children's Privacy

The App does not knowingly collect any personal information from anyone, including children under the age of 13. Since the App operates entirely offline and collects no personal data whatsoever, it is safe for users of all ages.

## 10. Data Security

All data generated by the App is stored locally on your device using Android's built-in secure storage mechanisms. Because the data never leaves your device, its security is guaranteed by your device's operating system and screen lock protections.

## 11. Data Retention and Deletion

All App data is stored locally on your device. You can delete all data at any time by:

- Clearing the App's data through your device's Settings → Apps → FlowShift → Storage → Clear Data.
- Uninstalling the App, which automatically removes all associated data.

## 12. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect changes in our practices or app features. Any changes will be reflected on this page with an updated "Effective Date."

## 13. Contact Us

If you have any questions or concerns about this Privacy Policy or the App's data practices, please contact us at:

**Email:** [Your Contact Email]

---

© 2026 FlowShift – Stop Scrolling. All rights reserved.
