# Countdown Widgets: Handoff (updated)

Status doc for any AI or developer continuing this project. Read fully before changing code.

## 1. What this is
An Android app (Capacitor 6 hybrid) plus three native home-screen widgets (Text, Photo, Countdown). The WebView app (`www/index.html`) is the widget editor, adapted from the demo `index_-_2026-11-02T234300_603.html`. Widgets are native Kotlin and render themselves to Bitmaps shown in an ImageView inside RemoteViews.

- appId / package: `com.countdown.widgets`
- Android only. minSdk 26, compile/target 34, Kotlin 1.9.22, AGP 8.2.1, Gradle 8.2.1

## 2. Status
All text files in the original plan are written. NOTHING HAS BEEN COMPILED OR RUN YET. Expect a few compile errors on the first build; fix them first.

Not written because a text-only tool cannot create binary files (add these by hand):

- `android/app/src/main/res/font/*.ttf`: Instrument Sans, Fraunces, Playfair Display, Lora, Caveat, Bebas Neue, Space Mono, Orbitron, VT323, Share Tech Mono, Audiowide, Oxanium, Geist Mono. File names must be lowercase with underscores (instrument_sans.ttf, geist_mono.ttf, ...). Until they exist, `FontLoader` falls back to system fonts, so the build still passes.
- `res/drawable/preview_text.png`, `preview_photo.png`, `preview_countdown.png`: optional. Widget picker previews currently use `previewLayout` (Android 12+). For older Android, add the PNGs and add `android:previewImage="@drawable/preview_text"` (and the others) to the three `res/xml/*_widget_info.xml` files.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`: run `gradle wrapper --gradle-version 8.2.1` inside `android/`, or just open `android/` in Android Studio.

## 3. How to build
1. `cd CountdownWidgets && npm install`
2. Add fonts (optional, see above).
3. `npx cap sync android` (copies `www/` into the app, regenerates `capacitor.settings.gradle`). Do NOT run `npx cap add android`; the android folder already exists.
4. Open `android/` in Android Studio, run on a device, then long-press the home screen and add the Text, Photos or Countdown widget.

## 4. Architecture
- `www/index.html`: the editor. All three widget types, themes, sidebar, color picker, date picker. Talks to native through `window.WidgetBridge` (`www/bridge.js`). Without Capacitor it runs in demo mode.
- `bridge.js` methods: init, saveWidget, deleteWidget, savePhoto, deletePhoto, pinWidget, finish.
- `WidgetBridgePlugin.kt`: Capacitor plugin `WidgetBridge`. `init` returns all saved configs plus launch context `{mode:'app'|'config', appWidgetId, type}`.
- `WidgetStore.kt`: SharedPreferences, one JSON per config (`w_<id>`). A config binds to at most one `appWidgetId`. `put()` keeps the native-owned `appWidgetId` if the editor copy has 0.
- `PhotoStorage.kt`: copies picked photos into `filesDir/photos/<configId>/`, downscaled to 1600 px with rotation baked in.
- `WidgetRenderer.kt` (+ `SevenSegment`, `FontLoader`, `CountdownMath`): draws each widget type to a Bitmap, mirroring the editor's CSS/JS.
- `WidgetActions.kt`: builds RemoteViews, tap handling, photo stepping. Providers are thin.
- `Ticker` (in `TickWorker.kt`): inexact AlarmManager chain (countdown each minute, photo on its interval) plus a 15 min WorkManager safety net. `BootReceiver` re-arms after boot and receives the TICK alarm.
- `WidgetConfigActivity.kt`: a BridgeActivity hosting the same editor. Opened by the launcher when a widget is added, and by a center tap on the widget. A floating Done button (added by the JS in config mode) calls `finishConfig`, which returns RESULT_OK so the widget is placed.
- `PhotoViewerActivity.kt`: full screen photo, left/right third = prev/next, middle = close.

## 5. Behaviours the user asked for
1. Three separate widgets in the picker, each with its own preview.
2. Each placed widget has separate settings (config per `appWidgetId`).
3. Photo widget taps (default `tap: 'zones'`): left third = previous photo, right third = next photo, center = open that widget's settings. Other options remain in the editor's Actions tab. Implemented as three FrameLayout zones in `widget_photo.xml`.
4. The photo widget has no border or outline (border option removed from the photo style tab; shadow stays).
5. "Add to home screen" button in the editor (not shown in config mode) uses `requestPinAppWidget`; `WidgetConfigActivity` binds the pending config automatically.

## 6. Android limits (already designed around)
- No per-second redraw: countdown refreshes about once a minute while the device is awake. The editor still shows seconds; the widget value is as of the last refresh.
- Photo auto-change below about 15 s is not guaranteed. Zoom/slide transitions are not animated on the widget.
- Frosted blur is not possible on a widget; only the translucent fill is drawn.
- Long-press menu is not possible on widgets (the launcher owns it). The editor still has the menu for the in-app preview.
- Countdown with many units is scaled down to fit instead of wrapping to several lines.

## 7. Things to verify first
1. Compile errors (nothing was built).
2. Unit order: the editor JS does `UNITS.filter(...).reverse()` and UNITS starts with years, so the preview shows seconds first. `WidgetRenderer.drawCount` copies that on purpose. If the editor order is a demo bug, fix both the JS (`fill()`) and the Kotlin (`.reversed()`).
3. Variable fonts: `FontLoader` uses `Typeface.create(base, weight, italic)` (API 28+). Variable TTFs may ignore weight; use static weights or `Typeface.Builder(...).setFontVariationSettings`.
4. The editor loads fonts from Google Fonts, so font previews need internet. Bundling the same files under `www/fonts` with `@font-face` would make it offline-safe.
5. Capacitor serves photo files through `convertFileSrc('file://' + path)`. If thumbnails do not show in the editor, check that URL.
6. `finishConfig` and photo saving run on a plugin thread; renderer work is quick but keep an eye on ANRs with large photos.
7. A deleted placed widget keeps its config in the editor library (unbound). Delete it from the sidebar.

## 8. Next work
Build and fix compile errors, add fonts, test on a device: placing each widget, center tap, left/right taps, minute refresh, reboot, resize, dark/light launcher.
