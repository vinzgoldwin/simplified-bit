# Simplified Fit

A private Android health dashboard for Fitbit data, with a Mac-hosted Codex coach.

## What it shows

- Steps
- Provisional readiness score
- Provisional sleep score
- Latest and resting heart rate
- Daily total and active calories
- A focused Codex chat through the Mac companion

Google Health does not publish its exact readiness formula or Fitbit sleep score formula, so both scores are calculated locally. Readiness follows Google's published inputs and requires seven valid sleep nights before a score appears; the personal baseline keeps improving through 30 days.

## Install the Android app

The ready-to-sideload APK is in `dist/SimplifiedFit-0.1.0.apk`.

1. Copy it to the Android phone.
2. Allow installs from the file manager when Android asks.
3. Open the APK and install.

To rebuild:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest assembleDebug
```

The delivered APK is signed with the local key in `signing/simplified-fit.jks`. Preserve that key and `release.properties`; Android requires the same key for future in-place upgrades.

## Connect Google Health

1. Create a Google Cloud project and enable Google Health API.
2. Create a Web Server OAuth client with `https://www.google.com` as its authorized redirect URI.
3. Add your Google account as a test user, or publish the consent screen to avoid seven-day testing refresh tokens.
4. In Simplified Fit, open Settings and paste the client ID and secret.
5. Tap **Open Google consent** and approve the three read-only scopes. The final `google.com` page may be blank; copy its complete address from the browser bar.
6. Return to Simplified Fit, paste that redirected URL (or only its `code` value), and tap **Connect Google Health**.

Credentials and refresh tokens are AES-GCM encrypted with an Android Keystore key. Health summaries are kept locally for 30 days. Background sync runs every six hours on a connected network.

## Run the Mac Coach

Build the companion:

```sh
cd companion
./build-app.sh
```

Open `companion/build/Simplified Fit Companion.app`. Its `SF` menu-bar item can turn the Coach on or off and copy the pairing details. Paste those details in Android Settings. Both devices should be on the same Tailscale network.

The companion invokes the already-authenticated local Codex CLI with a read-only sandbox. It does not use an OpenAI API key. Keep the Mac awake while chatting.

## Score formulas

Sleep score:

- Duration: 40%
- Restfulness: 20% (sleep efficiency and awake time)
- Restlessness: 20%
- REM stage balance: 10%
- Deep stage balance: 10%

Readiness score:

- HRV compared with the previous 28 days using personal baseline deviation
- Average sleep score across the past 7 valid nights
- Resting heart rate compared with the previous 28 days using inverse personal baseline deviation
- Physiological signals are centered at 50 and move 13 points per baseline standard deviation
- The three available signals are equally weighted and missing signals are reweighted
- Prior-day activity is intentionally excluded, matching Google's current readiness model

Restlessness, REM, and deep-stage signals are reweighted when unavailable instead of treated as zero. Restlessness is capped at 22 minutes for scoring, calibrated so the reference night with 12 restless minutes scores 88. Stage scores are strongest inside typical sleep-stage ranges, rather than rewarding more stage minutes without limit. These are wellness indicators, not medical scores.
