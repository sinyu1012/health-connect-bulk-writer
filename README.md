# HealthConnectMassWriter

Small native Android tool app for requesting Health Connect permissions and writing synthetic bulk Health Connect data.

## What it does

- Requests all supported Health Connect read/write permissions for the AndroidX record types listed in `HealthConnectCatalog`.
- Provides two write profiles:
  - `核心压测`: Heart rate, steps, distance, active calories, and HRV, following the OtterLife mass-data scale model.
  - `全类型模拟`: One synthetic generator for every Health Connect record type covered by the app.
- Uses the selected heart-rate count as the scale baseline. Heart rate is generated every 10 seconds, and every other type derives its count from its own interval.
- Writes records through `HealthConnectClient.insertRecords()` in batches, with progress and cancel support.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## License

MIT
