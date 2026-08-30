# Aalu Browser 🔒🥔

A single-purpose Android browser that **only** opens
[`google.com`](https://www.google.com) and [`github.com`](https://www.github.com).
Everything else — YouTube, video CDNs, every other domain — is **strictly blocked**
at every layer of the network stack, and videos never play, even when the URL
stays on `google.com`.

## What's locked down

| Layer | What it does |
| --- | --- |
| **URL allowlist** | Only `google.com` / `www.google.com` / `github.com` / `www.github.com` can be loaded. Everything else is rejected before it ever reaches the WebView. |
| **YouTube hard-block** | `youtube.com`, `youtu.be`, `googlevideo.com`, `ytimg.com`, `vimeo.com`, etc. are denied by substring match. |
| **Video MIME block** | Network-level interception blocks `video/*`, `application/x-mpegurl` (HLS), and MPEG-DASH manifests. |
| **DOM strip** | Injected JavaScript removes every `<video>`, `<audio>`, and `<iframe>` pointing at any video host — and re-runs via `MutationObserver` so dynamically added players are also killed. |
| **API override** | `HTMLMediaElement.prototype.play` and `.load` are overridden to no-op so even a `<video>` that slips through the DOM strip cannot start. |
| **Strict SSL** | Any SSL error aborts the request — no "proceed anyway" button. |
| **No URL bar** | The user cannot type a URL. The app always starts on the Google home page. |
| **No popups** | `onCreateWindow` returns false — no new tabs, no `window.open`. |
| **No fullscreen video** | `onShowCustomView` refuses to hand control over. |
| **No downloads** | `setDownloadListener` is a no-op. |
| **No file access** | `allowFileAccess=false`, `allowContentAccess=false`, `allowFileAccessFromFileURLs=false`. |
| **No sensors** | `onPermissionRequest` always denies (camera, mic, geolocation, etc.). |
| **No mixed content** | HTTPS pages never silently fall back to HTTP. |
| **Cleartext disabled** | The manifest sets `usesCleartextTraffic="false"`. |
| **Single Activity** | No `<data>` intent-filter, so other apps cannot hand us a URL to navigate to. |

## How to build the APK

You don't need Android Studio. The build is fully automated.

### Option 1 — One-click via GitHub (recommended)

1. Create a new GitHub repository.
2. Upload the **contents** of this folder (not the folder itself) — every file
   at the root, including `.github/`, `app/`, `gradle/`, `gradlew`, etc.
3. Push to `main` (or `master`).
4. Open the **Actions** tab on GitHub — a build starts automatically.
5. When it finishes, scroll down to **Artifacts** and download either
   `aalu-browser-debug` or `aalu-browser-release`. Both APKs are debug-signed
   so they install on any device.
6. Transfer the `.apk` to the phone, tap to install (allow "install from
   unknown sources" if prompted).

### Option 2 — Build locally

You need JDK 17 and the Android SDK (or Android Studio).

```bash
./gradlew assembleDebug
# APK lands at:  app/build/outputs/apk/debug/app-debug.apk
```

## How to add or remove an allowed site

Open `app/src/main/java/com/aalu/browser/Allowlist.kt` and edit
`allowedHosts` (or `deniedHostFragments` for hard-blocks). The whole filter
reads from this one file.

## Project layout

```
AaluBrowser/
├── .github/workflows/build.yml   ← auto-builds the APK on every push
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aalu/browser/
│       │   ├── MainActivity.kt
│       │   ├── StrictWebViewClient.kt
│       │   ├── VideoBlocker.kt
│       │   └── Allowlist.kt
│       └── res/
│           ├── layout/view_blocked.xml
│           ├── values/{strings,colors,themes}.xml
│           ├── drawable/ic_launcher_foreground.xml
│           └── mipmap-*/ic_launcher{,_round}.png
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.{jar,properties}
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── README.md
```

## Tested with

- minSdk 24 (Android 7.0)
- targetSdk 34 (Android 14)
- Kotlin 1.9.22
- AGP 8.2.2 / Gradle 8.5
