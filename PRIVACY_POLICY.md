# Privacy Policy — Resource monitor (`com.kotekompotek.resourcemonitor`)

> Published at https://kotekompotek.github.io/resource-monitor/privacy-policy.html
> (GitHub Pages from `docs/privacy-policy.html`). Paste this link into
> Play Console → Store listing, the in-app `PRIVACY_POLICY_URL` constant
> in `MainActivity.kt`, and the Data safety section.

Effective date: 2026-09-26. Contact: [REPLACE WITH SUPPORT EMAIL].

## 1. What this app does

Resource monitor shows a small movable on-screen widget with your device's
CPU frequency, RAM, disk space, battery temperature, battery current,
network speed and ping latency, plus history graphs.

## 2. Data we collect: none

- All readings (CPU, RAM, disk, battery, traffic counters) are obtained from
  Android system APIs and displayed **only on your device**. They are never
  transmitted, stored on servers, shared, or sold.
- The Ping feature sends **one ICMP echo request per 5 seconds to 8.8.8.8**
  to measure latency. The request contains no personal data.
- Settings (which metrics are shown, precision, widget size) are stored
  **only in this device's app preferences**.
- The app has **no accounts, no analytics, no advertising, no third-party SDKs**.

## 3. Permissions and why they are needed

| Permission | Purpose |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Core feature: the movable monitor widget. Requested only after an in-app explanation; revocable anytime in system settings. |
| Internet / network state (`INTERNET`, `ACCESS_NETWORK_STATE`) | Measuring traffic speed and ping latency. |
| Foreground service, special use (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Keeping the monitor running with a persistent, user-visible notification that can stop it. |
| Notifications (`POST_NOTIFICATIONS`, Android 13+) | Showing that persistent notification. The monitor works even if you deny it. |

## 4. Data retention and deletion

No personal data leaves the device, so there is nothing to retain or delete.
Uninstalling the app removes its on-device settings.

## 5. Children

The app is a general-utility tool, not directed at children, and collects
no data from anyone.

## 6. Changes

Material changes to this policy will be reflected in the app's store listing
before they take effect.
