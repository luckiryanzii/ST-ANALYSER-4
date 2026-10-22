# ST-ANALYSER v0.6 — GitHub-ready Android source

A real, live audio-output spectrum analyser for Android, with dynamic music metadata and media controls.

## Changes to this build

- **Previous:** ONE large illuminated, left-pointing arrow, on the left.
- **Play/Pause:** luminous play triangle **inside a circular outline** whenever playback is stopped/paused. Changes to **two illuminated pause bars** whenever music is playing.
- **Next:** ONE large illuminated, right-pointing arrow, on the right.
- **Song title:** live, UPPERCASE, thin sans-serif dot-matrix LEDs; brisk entrance then hold, continuous scroll, static, or hidden setting.
- **Branding:** bold, larger, heavily italic ST in RED and bold italic ANALYSER matching the selected scheme. Both made of illuminated square LEDs, not image assets.
- Existing audio analysis, spectrum pixels, palettes, glow, fading segments, reflections, Android navigation, notification-listener media integration, and the no-microphone policy are preserved.

The source contains no baked-in track title, no microphone capture code, and no microphone fallback. Android's Visualizer API nevertheless requires RECORD_AUDIO permission to monitor the device output mix. Optional Notification Access is required by Android to discover active media sessions; Android's broad warning about notifications remains applicable.

## Build on GitHub

Upload the **contents** of the extracted folder, not the enclosing folder, to the **repository root** (containing `app/`, `build.gradle.kts`, `settings.gradle.kts`, and `.github/workflows/build.yml`). Commit to main, then check Actions. The workflow installs Android SDK 35, Java 17, and Gradle 8.9, runs `gradle :app:assembleDebug`, and publishes an APK artifact.

*The package has been source-inspected, but not compiled or device-tested in this environment.*
