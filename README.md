# GasTrackerApp
Android Mobile client application for interacive tracking gas stations.

## Development
- Kotlin
- Gradle build tool (wrapped)

## Client
### Install and configure Android SDK for Linux:
1. run `./install_AndroidSDK.sh <install_path>`

### Build Debug (requires configured Android SDK):
1. run `./gradlew assemblyDebug`
2. APK files will be located in: `client/app/build/outputs/apk/`

### Build and install debug application on device:
1. turn on USB Debugging mode on the device.
2. connect your Androig target device
3. run `./gradlew installDebug`
4. check if application is installed on the device

