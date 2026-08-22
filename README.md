# Simplified Fit

A private Android health dashboard for Fitbit data, with a choice of direct OpenRouter coaching or a Mac-hosted Codex coach.

## What it shows

- Steps
- Recovery estimate
- Sleep score
- Latest and resting heart rate
- Daily total and active calories
- A focused Coach using OpenRouter or the local Codex companion

Google Health does not expose its readiness or Fitbit sleep scores through the API, so both are calculated locally. The recovery estimate is an independent wellness indicator rather than a copy of Google's proprietary score. It requires seven valid sleep nights before a score appears, and its personal baseline keeps improving through 28 days.

## Install the Android app

The ready-to-sideload APK is in `dist/SimplifiedFit.apk`.

1. Copy it to the Android phone.
2. Allow installs from the file manager when Android asks.
3. Open the APK and install.

To rebuild:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest :app:publishReleaseApk
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

## Configure Coach

Open **Settings → Coach** and choose a provider:

- **OpenRouter** calls the API directly from Android using OpenRouter's `~deepseek/deepseek-v4-flash-latest` alias. Paste an OpenRouter API key and save it. The key is encrypted with Android Keystore and never sent to the Mac.
- **Local Codex** uses the paired Mac companion and the already-authenticated Codex CLI. This mode does not require an API key, but the Mac must remain awake and reachable through Tailscale.

OpenRouter streams responses as they are generated. The current Codex CLI integration returns its completed answer at once; both providers use the same progress and evidence UI.
In OpenRouter mode, the compact health summary attached to a Coach question is sent to OpenRouter and its selected model provider.

## Run the Mac Coach for Codex

Build the companion:

```sh
cd companion
./build-app.sh
```

Open `companion/build/Simplified Fit Companion.app`. Its `SF` menu-bar item can turn the Coach on or off and copy the pairing details. Paste those details in Android Settings. Both devices should be on the same Tailscale network.

The companion invokes the already-authenticated local Codex CLI with a read-only sandbox. It does not use an OpenAI API key. Keep the Mac awake while chatting in Local Codex mode.

## Score formulas

Sleep score:

- Duration against the healthy sleep range: 45%
- Continuity from sleep efficiency and awake time: 20%
- Restlessness: 10%
- REM stage balance: 12.5%
- Deep stage balance: 12.5%

Recovery estimate:

- Nightly HRV is log-transformed and compared with the previous 28 days
- Resting heart rate is compared with the previous 28 days in the opposite direction
- Personal deviations become bounded percentiles without a fitted starting score
- HRV and resting heart rate form one autonomic-recovery signal; neither can erase a warning from the other
- Autonomic recovery contributes 70%; sleep duration and timing consistency across 7 nights contributes 30%
- There are no score floors, severe-day patches, or Google-specific calibration constants

Restlessness, REM, and deep-stage signals are reweighted when unavailable instead of treated as zero. Stage scores are strongest inside typical sleep-stage ranges rather than rewarding more stage minutes without limit. Across the 11 nights available during calibration, the local sleep score stays within two points of Fitbit. These are wellness indicators, not medical scores.
