<br/>
<div align="center">
<p align="center">
  <img width="234" src="https://raw.githubusercontent.com/netbirdio/netbird/main/docs/media/logo-full.png"/>
</p>
  <p>
     <a href="https://github.com/netbirdio/netbird/blob/main/LICENSE">
       <img height="20" src="https://www.gnu.org/graphics/gplv3-88x31.png" />
     </a>
    <a href="https://docs.netbird.io/slack-url">
        <img src="https://img.shields.io/badge/slack-@netbird-red.svg?logo=slack"/>
     </a>    
  </p>
</div>


<p align="center">
<strong>
  Start using NetBird at <a href="https://netbird.io/pricing">netbird.io</a>
  <br/>
  See <a href="https://netbird.io/docs/">Documentation</a>
  <br/>
   Join our <a href="https://docs.netbird.io/slack-url">Slack channel</a>
  <br/>

</strong>
</p>

<br>

# NetBird Android client

The NetBird Android client allows connections from mobile devices running Android to private resources in the NetBird network.

## Screenshots

<p align="center">
  <img src="docs/screenshots/mainscreen.png" alt="mainscreen" width="250" style="margin-right: 10px;"/>
  <img src="docs/screenshots/peer-overview.png" alt="peer-overview" width="250" style="margin-right: 10px;"/>
  <img src="docs/screenshots/peer-detail.png" alt="peer-detail" width="250"/>
</p>

## Install
You can download and install the app from the Google Play Store:

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=io.netbird.client)

## Remote control

This fork adds an opt-in interface that lets another app on the device, such as
a TV launcher, connect and disconnect NetBird without its UI appearing. The
design follows the WireGuard Android client: a dangerous permission, an exported
receiver, and an in-app setting that is off by default.

### Install a self-signed build

A build signed with a key other than NetBird's upload key cannot update the Play
Store build in place. Uninstall the store app first:

```shell
adb uninstall io.netbird.client
adb install app-release.apk
```

Uninstalling removes the app's data, so the profile and its login are gone.
Open NetBird once and sign in again before relying on remote commands. Granting
VPN consent in that first session is also what lets a later remote start bring
the service up from the background (see below).

### Permission

`io.netbird.client.permission.CONTROL_VPN`, protection level `dangerous`. A
control app declares it:

```xml
<uses-permission android:name="io.netbird.client.permission.CONTROL_VPN" />
```

and is granted it explicitly. Sideloaded builds do not show a runtime prompt, so
grant it from adb:

```shell
adb shell pm grant com.gathin.tvhome io.netbird.client.permission.CONTROL_VPN
```

### Setting

Settings → **Allow remote control apps**, off by default. While it is off every
command is ignored and answered with `DISABLED`.

### Commands

Send these to `io.netbird.client`, addressed with
`setPackage("io.netbird.client")` or the receiver component:

| Action | Effect |
| --- | --- |
| `io.netbird.client.action.CONNECT` | Connect with the current profile. Optional string extra `profile` switches to that profile first (matched by id, then by name); an unknown profile is answered with `ERROR`. |
| `io.netbird.client.action.DISCONNECT` | Disconnect. |
| `io.netbird.client.action.TOGGLE` | Connect if down, disconnect if up. |
| `io.netbird.client.action.GET_STATE` | Change nothing, report the state. |

Add `Intent.FLAG_INCLUDE_STOPPED_PACKAGES` if the command must reach an install
that has never been opened, or one that was force-stopped. Android otherwise
holds broadcasts back from stopped packages.

### State reports

After every command and on every engine state change NetBird sends
`io.netbird.client.action.STATE_CHANGED` with
`sendBroadcast(intent, "io.netbird.client.permission.CONTROL_VPN")`, so only
apps holding the permission receive it. Extras:

* `state` (string): `CONNECTING`, `CONNECTED`, `DISCONNECTING`, `DISCONNECTED`,
  `NEEDS_LOGIN`, `NEEDS_CONSENT`, `DISABLED`, or `ERROR`.
* `message` (string, optional): detail for `ERROR`.

Register the receiver at runtime:

```java
registerReceiver(receiver,
        new IntentFilter("io.netbird.client.action.STATE_CHANGED"),
        Context.RECEIVER_EXPORTED);
```

A receiver declared only in the manifest does not receive implicit broadcasts on
Android 8 and later, so a manifest-only receiver will not see these reports.

### Behaviour

Remote commands never open an activity, a dialog, or a notification prompt. If
VPN consent is missing the answer is `NEEDS_CONSENT`; if the profile has not
signed in, `NEEDS_LOGIN`. In both cases nothing else happens and the caller is
expected to open NetBird itself. Commands work with NetBird's process killed
(including `adb shell am force-stop`) and while the device is idle.

A background foreground-service start is what Android 12+ normally refuses. A
VPN app the user has consented to holds the `OP_ACTIVATE_VPN` app-op, which the
platform counts as an exemption (`REASON_OP_ACTIVATE_VPN` in
`ActiveServices`), and NetBird starts the same way the quick settings tile does.
Without VPN consent the start is not attempted at all: the command is answered
with `NEEDS_CONSENT`.

### Testing

`remotecontrol-test/` is a small sender app. It declares the permission, sends
the commands, and logs the replies it receives on the `NetBirdRemoteTest` tag.
It can be driven without any UI:

```shell
adb shell pm grant io.netbird.client.controltest io.netbird.client.permission.CONTROL_VPN
adb shell am broadcast -n io.netbird.client.controltest/.CommandReceiver \
    -a io.netbird.client.controltest.SEND \
    --es action io.netbird.client.action.CONNECT
adb logcat -s NetBirdRemoteTest NetBirdRemoteControl
```

Unit tests cover the state mapping and the default-off setting:
`./gradlew :app:testDebugUnitTest`.

## Reporting bugs and requesting features

NetBird uses a discussion-first workflow across all its repositories. Bug reports and
feature requests for the Android client start in
[netbird Discussions](https://github.com/netbirdio/netbird/discussions), not as issues here.

| What you want to do | Where to go |
| --- | --- |
| Report a bug, regression, or unexpected behavior | [Issue Triage](https://github.com/netbirdio/netbird/discussions/new?category=issue-triage) |
| Request a feature or share an idea | [Ideas & Feature Requests](https://github.com/netbirdio/netbird/discussions/new?category=ideas-feature-requests) |
| Ask about setup, configuration, or self-hosting | [Q&A / Support](https://github.com/netbirdio/netbird/discussions/new?category=q-a-support) |
| Report a security vulnerability | [Security policy](https://github.com/netbirdio/netbird/security/policy), never a public thread |

Our team triages each discussion. Validated reports become issues in this repository,
linked back to the discussion. See
[How to use Discussions, Issues, and Pull Requests](https://github.com/netbirdio/netbird/discussions/6075)
for the full workflow.

## Building from source
### Requirements
We need the following software:
* Java 1.11. Usually comes with Android Studio
* android studio initialized with jdk and emulator (not covered here, is a req from android-client project)
* gradle (https://gradle.org/install/)

### Prepare development environment
1. Close all repositories:
> assuming you use a path like ~/projects locally
```shell
mkdir ~/projects
cd projects
# clone netbird repo
git clone --recurse-submodules git@github.com:netbirdio/android-client.git
```
2. Checkout the repositories to the branches you want to test. If you want the latest, check the status information on your IDE or on https://github.com and verify the branch list and commit history.
3. Export JDK and Android home vars, on macOS they are: (please contribute with Linux equivalent)
```shell
# replace <USERNAME> with your name
export ANDROID_HOME=/Users/<USERNAME>/Library/Android/sdk
export JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```
4. Install NDK and CMake
```shell
cd ~/projects/android-client
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "ndk;23.1.7779620"
```
### Generate debug bundle
Follow the steps to run locally until the step 5 then run the following steps:
1. Build Go agent library
```shell
cd ~/projects/android-client
./build-android-lib.sh
```
2. Run gradlew
```shell
cd ~/projects/android-client/android
./gradlew bundleDebug  -PversionCode=123 -PversionName=1.2.3
```

## Remote control (fork)

This branch adds an opt-in remote-control API so another app on the same device
(a TV launcher, an automation app) can connect and disconnect NetBird without
opening NetBird's UI. It is modelled on the WireGuard Android client's
`com.wireguard.android.permission.CONTROL_TUNNELS` receiver and its
"Allow remote control apps" setting.

### Contract

The names below are the public surface. A caller codes against them, so do not
rename them.

**Permission**

```xml
<permission
    android:name="io.netbird.client.permission.CONTROL_VPN"
    android:protectionLevel="dangerous"
    android:label="control NetBird"
    android:description="Connect and disconnect NetBird" />
```

The controlling app declares it in its own manifest:

```xml
<uses-permission android:name="io.netbird.client.permission.CONTROL_VPN" />
```

It is a dangerous permission, so it is not granted at install. Grant it once,
after both apps are installed:

```shell
adb shell pm grant com.example.launcher io.netbird.client.permission.CONTROL_VPN
```

**Enabling it**: NetBird → Settings → **Allow remote control apps**. It is off
by default. While it is off, every command is ignored and answered with
`DISABLED`.

**Commands** are broadcasts to
`io.netbird.client.control.RemoteControlReceiver` (exported; the manifest's
`android:permission` keeps out apps without the control permission):

| Action | Effect |
| --- | --- |
| `io.netbird.client.action.CONNECT` | Connect with the current profile. |
| `io.netbird.client.action.DISCONNECT` | Disconnect. |
| `io.netbird.client.action.TOGGLE` | Connect if down, disconnect if up. |
| `io.netbird.client.action.GET_STATE` | Change nothing; report the state. |

`CONNECT` accepts an optional string extra `profile` with a profile's name or
ID; NetBird switches to it first if it exists. Address the broadcast to the
package (`intent.setPackage("io.netbird.client")`); a bare implicit broadcast
is not delivered to manifest receivers on Android 8 and later.

**State reports**: after every command and on every engine state change,
NetBird sends `io.netbird.client.action.STATE_CHANGED` with
`sendBroadcast(intent, "io.netbird.client.permission.CONTROL_VPN")`, so only
apps holding the permission receive it. The intent carries:

- `state`: `CONNECTING`, `CONNECTED`, `DISCONNECTING`, `DISCONNECTED`,
  `NEEDS_LOGIN`, `NEEDS_CONSENT`, `DISABLED` or `ERROR`;
- `message`: optional human-readable detail, used with `ERROR`.

**Behaviour**

- No activity, dialog, browser or notification prompt is ever opened because
  of a remote command.
- If VPN consent is missing (`VpnService.prepare()` returns non-null), the
  answer is `NEEDS_CONSENT` and nothing is started.
- If the user is not logged in, the answer is `NEEDS_LOGIN` and nothing is
  started. The caller opens NetBird itself when it wants to.
- Commands work from a cold start (the broadcast starts the app's process) and
  while the device has been idle.

**From a launcher**

```java
Intent command = new Intent("io.netbird.client.action.CONNECT");
command.setPackage("io.netbird.client");
command.putExtra("profile", "default");          // optional
sendBroadcast(command);

// Replies:
IntentFilter filter = new IntentFilter("io.netbird.client.action.STATE_CHANGED");
registerReceiver(receiver, filter, "io.netbird.client.permission.CONTROL_VPN", null);
```

### Background starts on Android 12+

A broadcast receiver runs with NetBird in the background, and Android 12 (API
31) and later refuse a `startForegroundService()` from the background unless an
exemption applies. The relevant exemption here is the VPN app-op: once the user
has granted the VPN consent dialog, the package holds `OP_ACTIVATE_VPN`, which
`ActiveServices` accepts as `REASON_OP_ACTIVATE_VPN`. This is the same mechanism
that lets the WireGuard client bring a tunnel up from its receiver.

The receiver checks `VpnService.prepare()` *before* it starts anything, so the
first connect of a fresh install reports `NEEDS_CONSENT` and never attempts a
background start. After consent, the service start is accepted; no try/catch
masks a refusal. If consent has been revoked or an OEM blocks it, the start
fails and the caller receives `ERROR` with a message, not a fake `CONNECTING`.

This was verified on a Xiaomi Google TV (MiTV-MOEU0, Android 14 / API 34):
with the NetBird process force-stopped, a `CONNECT` produced
`CONNECTING → CONNECTED`, and `dumpsys activity services` reported
`allowStartForeground=OP_ACTIVATE_VPN`, `uidState: RCVR` and
`isForeground=true types=40000000` (`FOREGROUND_SERVICE_SPECIAL_USE`).

### Installing this fork's build

A self-signed build **cannot update the Play Store build in place**: Android
requires an APK update to be signed with the same key as the installed one.
Uninstall the store app first. That removes its data, so the user has to log in
again afterwards.

```shell
adb uninstall io.netbird.client
adb install app-release.apk
# Launch NetBird once and log in (SSO, setup key or the self-hosted server),
# then switch on Settings -> Allow remote control apps.
adb shell pm grant com.gathin.tvhome io.netbird.client.permission.CONTROL_VPN
```

`pm grant` only works if the launcher already declares the
`io.netbird.client.permission.CONTROL_VPN` `<uses-permission>` in its manifest.

### Test app

`remotecontrol-test/` is a test-only sender module (`io.netbird.client.controltest`).
It declares the control permission, sends every command and logs the states it
receives. Grant it the permission, then drive it from adb:

```shell
adb shell pm grant io.netbird.client.controltest io.netbird.client.permission.CONTROL_VPN
adb shell am broadcast -n io.netbird.client.controltest/.CommandReceiver \
    -a io.netbird.client.controltest.SEND \
    --es action io.netbird.client.action.CONNECT
adb logcat -s NetBirdRemoteTest NetBirdRemoteControl
```

The receiver path is used (rather than `am broadcast` straight to NetBird)
because the shell user cannot hold a custom dangerous permission.
