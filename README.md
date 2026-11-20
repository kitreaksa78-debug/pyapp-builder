# Pool Overlay Robot (Android Project - v5)

Fixed Gradle plugin resolution in offline/restricted `pyapp-builder` environment:
- Configured `settings.gradle` with `pluginManagement` repositories (`google()`, `mavenCentral()`, `gradlePluginPortal()`) and `dependencyResolutionManagement`.
- Root `build.gradle` uses `buildscript { repositories { ... } dependencies { classpath 'com.android.tools.build:gradle:8.5.2' } }` and legacy `apply plugin: 'com.android.application'` in `app/build.gradle` so Gradle resolves the AGP plugin cleanly.
