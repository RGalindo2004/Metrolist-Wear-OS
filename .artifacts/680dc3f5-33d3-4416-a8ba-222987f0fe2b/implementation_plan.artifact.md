# Fix Google Auth 401 Error on Wear OS

The user reports a "Google Error 401" when attempting to log in via the device code flow on their Wear OS device. This error typically indicates that the `client_id` is invalid or not authorized for the device flow, or that a `client_secret` is required but missing.

## Proposed Changes

### [core]

#### [MODIFY] [GoogleDeviceAuth.kt](file:///C:/Users/ramir/AndroidStudioProjects/Metrolist/core/src/main/kotlin/com/metrolist/music/utils/GoogleDeviceAuth.kt)
- Update `CLIENT_ID` to a more robust one (Smart YouTube TV).
- Add `CLIENT_SECRET`.
- Include `client_secret` in `requestDeviceCode` and `pollToken` requests.
- Add a `User-Agent` header to the `HttpClient` to avoid potential blocking by Google.

## Verification Plan

### Automated Tests
- I will run the existing build command to ensure no regressions in compilation.
```bash
./gradlew :app:assembleFossDebug
```
(Note: The user instructions mentioned this command for the main app, but I should also check `:wear:assembleDebug` if possible).

### Manual Verification
- The user will need to test the "Code Login" on the watch again.
- If the fix works, the watch should display a code and the URL `https://google.com/device`.
