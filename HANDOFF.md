# Claude Code Handoff — Android SMS Delete APK

## What this is

An Android app (`SmsDeleteApp/`) was built and pushed to this repo but could not be
compiled or have its CI workflow iterated on due to missing `workflows` permission in
the previous session. Your job is to:

1. Build the APK locally and confirm it compiles
2. Replace the GitHub Actions workflow with a self-reporting version (release on
   success, issue on failure)
3. Push and iterate until a GitHub Release exists with `app-debug.apk` attached

---

## Repo

| | |
|---|---|
| **Repo** | `https://github.com/akshaybhatnagar-msft/ReconData` |
| **Branch** | `claude/android-sms-delete-app-Fmr3Z` |
| **App dir** | `SmsDeleteApp/` |

---

## Step 1 — Clone

```bash
git clone https://github.com/akshaybhatnagar-msft/ReconData.git
cd ReconData
git checkout claude/android-sms-delete-app-Fmr3Z
```

---

## Step 2 — Verify / install environment

```bash
java -version       # need 17+
echo $ANDROID_HOME  # must be set and point to a valid SDK
sdkmanager --version
```

If Android SDK is missing:

```bash
# Download cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
mkdir -p $HOME/android-sdk/cmdline-tools
unzip cmdtools.zip -d $HOME/android-sdk/cmdline-tools
mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest

# Set env vars (add to ~/.bashrc or ~/.zshrc to persist)
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses and install required components
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

---

## Step 3 — Build locally

```bash
cd SmsDeleteApp
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper --gradle-version 8.7    # generates the missing gradle-wrapper.jar
chmod +x gradlew
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Fix any compile errors in `app/src/main/java/com/smsdelete/app/` before moving on.

---

## Step 4 — Replace the workflow file

Replace the entire contents of `.github/workflows/build-apk.yml` with the following.
This version creates a **GitHub Release** (with APK attached) on success, and opens a
**GitHub Issue** (with Gradle log) on failure — so the result is always visible without
needing Actions API access.

```yaml
name: Build Debug APK

on:
  push:
    branches: ['**']
  workflow_dispatch:

permissions:
  contents: write
  issues: write

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Accept Android SDK licenses
        run: yes | sdkmanager --licenses 2>/dev/null || true

      - name: Install required SDK components
        run: sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

      - name: Generate Gradle wrapper JAR
        working-directory: SmsDeleteApp
        run: gradle wrapper --gradle-version 8.7

      - name: Make gradlew executable
        working-directory: SmsDeleteApp
        run: chmod +x gradlew

      - name: Create local.properties
        working-directory: SmsDeleteApp
        run: echo "sdk.dir=$ANDROID_HOME" > local.properties

      - name: Build debug APK
        working-directory: SmsDeleteApp
        shell: bash
        run: |
          set -o pipefail
          ./gradlew assembleDebug --no-daemon --stacktrace 2>&1 | tee /tmp/build.log

      - name: Publish APK as GitHub Release
        if: success()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh release create "apk-build-${{ github.run_number }}" \
            --title "SMS Delete APK — Build ${{ github.run_number }}" \
            --notes "Built from \`${{ github.ref_name }}\` @ \`${{ github.sha }}\`" \
            --prerelease \
            "SmsDeleteApp/app/build/outputs/apk/debug/app-debug.apk#app-debug.apk"

      - name: Open issue with build log on failure
        if: failure()
        uses: actions/github-script@v7
        with:
          script: |
            const { execSync } = require('child_process');
            let log = 'Build log not available';
            try {
              log = execSync('tail -c 8000 /tmp/build.log 2>/dev/null').toString();
            } catch(e) {}
            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `[CI] Build failed — run #${context.runNumber}`,
              body: [
                `## Build failed on \`${context.ref}\``,
                ``,
                `**Run:** ${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`,
                `**Commit:** \`${context.sha}\``,
                ``,
                `### Last 8 KB of Gradle output`,
                '```',
                log,
                '```'
              ].join('\n')
            });
```

Commit and push:

```bash
cd /path/to/ReconData   # repo root, not SmsDeleteApp/
git add .github/workflows/build-apk.yml
git commit -m "ci: self-reporting workflow — release on success, issue on failure"
git push origin claude/android-sms-delete-app-Fmr3Z
```

---

## Step 5 — Monitor and iterate

After each push, check results via the GitHub MCP tools (both are available in Claude
Code with the `akshaybhatnagar-msft/ReconData` MCP server configured):

**Build succeeded →** `mcp__github__list_releases` will show a prerelease named
`apk-build-N` with `app-debug.apk` as a release asset. That URL is the public
download link — hand it to the user.

**Build failed →** `mcp__github__list_issues` will show a `[CI] Build failed` issue
containing the last 8 KB of the Gradle log. Read the error, fix the relevant source
file(s), commit, push, and the workflow re-runs automatically. Repeat until green.

---

## App summary (for context when fixing errors)

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Material3 / ViewBinding |
| **minSdk** | 29 (Android 10) |
| **targetSdk** | 35 |
| **Package** | `com.smsdelete.app` |

**What it does:** lets the user bulk-delete received SMS messages from a selectable
time window — 30 min, 1 hour (default), 2 hours, or 2 days. Android 10+ requires the
app to hold `ROLE_SMS` to delete messages; the app requests the role at delete time
and shows a live preview of messages that will be deleted.

**Source files:**

```
SmsDeleteApp/app/src/main/java/com/smsdelete/app/
├── MainActivity.kt          # UI, duration toggle, delete flow
├── SmsHelper.kt             # ContentResolver queries and deletion
├── SmsEntry.kt              # Data class
├── SmsPreviewAdapter.kt     # RecyclerView adapter for message preview
├── SmsReceiver.kt           # Stub required for ROLE_SMS
├── MmsReceiver.kt           # Stub required for ROLE_SMS
├── ComposeSmsActivity.kt    # Stub required for ROLE_SMS
└── HeadlessSmsSendService.kt# Stub required for ROLE_SMS
```
