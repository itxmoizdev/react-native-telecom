# react-native-telecom

### The modern **react-native-callkeep** alternative for New Architecture

**Native incoming / outgoing call UI for React Native** — Android Jetpack **Core-Telecom**, CallStyle notifications, lock-screen full-screen UI, ringtone, mute, hold, keypad, speaker / Bluetooth audio routes, and FCM wake hooks.

> Looking for **react-native-callkeep**, **CallKeep**, **ConnectionService**, **CallKit-style VoIP UI**, or a **TurboModule telecom** library that works with **React Native New Architecture (Fabric)**? This package is built for that.

[![npm version](https://img.shields.io/npm/v/react-native-telecom.svg)](https://www.npmjs.com/package/react-native-telecom)
[![license](https://img.shields.io/npm/l/react-native-telecom.svg)](./LICENSE)
[![platform](https://img.shields.io/badge/platform-Android-green.svg)](./docs/android-setup.md)
[![arch](https://img.shields.io/badge/New%20Architecture-TurboModule-blue.svg)](https://reactnative.dev/docs/the-new-architecture/landing-page)

**npm:** `react-native-telecom` · **GitHub:** [itxmoizdev/react-native-telecom](https://github.com/itxmoizdev/react-native-telecom)

---

## Why developers search for this (and leave CallKeep)

| Problem with older stacks | What `react-native-telecom` does |
|---------------------------|----------------------------------|
| `react-native-callkeep` breaks or fights **New Architecture / Reanimated / Fabric** | **TurboModule-first** Kotlin API |
| Legacy Android **ConnectionService** path is dated | Official **Jetpack Core-Telecom** (`androidx.core:core-telecom`) |
| Hard to get **native CallStyle** + full-screen on OEMs (Vivo, Xiaomi…) | CallStyle FGS + full-screen Activity + ringtone player |
| Want WhatsApp / Phone-app style UI | Pixel-style incoming + in-call screens (overridable layouts) |
| Need Telnyx / WebRTC / LiveKit / Twilio | Library owns UI + Telecom; you attach media on audio-session events |

**Keywords people use that this README targets:**  
`react-native-callkeep`, `callkeep alternative`, `react native voip`, `react native incoming call`, `react native call notification`, `ConnectionService`, `Core Telecom`, `CallStyle`, `full screen intent`, `react native telecom`, `TurboModule calls`, `New Architecture callkeep`, `FCM incoming call`, `Telnyx react native`, `WebRTC call UI`.

---

## Features

### Native Android call experience
- **CallStyle** system notification (Answer / Decline / End)
- **Full-screen incoming UI** over the lock screen (`showWhenLocked` + `USE_FULL_SCREEN_INTENT`)
- **Outgoing + in-call UI** (mute, keypad, audio route, hold, end)
- **Default ringtone + vibration** (respects silent / vibrate / normal)
- Quiet ongoing notification after answer (no stuck ringing)

### VoIP app plumbing
- Jetpack **Core-Telecom** session lifecycle
- **Mute / hold** via Telecom + `AudioManager`
- **Audio routes:** earpiece · speaker · Bluetooth SCO
- **DTMF** local tones + JS `dtmfTone` events (send over your signaling / WebRTC)
- **Caller avatar** URL (HTTPS / file) with initials fallback
- **FCM / push wake** via `TelecomPushBridge` when JS is dead
- **Audio session events** to attach Telnyx, WebRTC, LiveKit, etc.

### Developer experience
- React Native **New Architecture** / **TurboModules**
- TypeScript API
- Overridable XML layouts for brand UI
- Example app included

### Platform status
| Platform | Status |
|----------|--------|
| **Android** | Supported (minSdk **26**) |
| **iOS (CallKit)** | Planned — not in v0.x |

---

## Installation (npm / yarn / pnpm)

```bash
npm install react-native-telecom
# or
yarn add react-native-telecom
# or
pnpm add react-native-telecom
```

**Requirements**
- React Native **New Architecture** enabled
- Android **minSdk 26+**
- **Physical device** for reliable Telecom / CallStyle testing

Full permissions & OEM notes: [docs/android-setup.md](./docs/android-setup.md)

---

## Quick start

```ts
import Telecom from 'react-native-telecom';

await Telecom.setup({
  appName: 'My App',
  supportsVideo: true,
  ui: {
    backgroundColor: '#F7F4EF',
    launchFullScreenOnIncoming: true,
  },
});

// Attach real media (Telnyx / WebRTC / LiveKit) here
Telecom.addEventListener('didActivateAudioSession', ({ callUUID }) => {
  // start peer connection / Telnyx call for callUUID
});

Telecom.addEventListener('didDeactivateAudioSession', () => {
  // tear down media
});

Telecom.addEventListener('dtmfTone', ({ digit }) => {
  // insertDTMF / SIP INFO / your signaling
});

// Incoming (optional avatar URL)
Telecom.displayIncomingCall(
  uuid,
  '+15551234567',
  'Ada Lovelace',
  false,
  false,
  'https://example.com/ada.jpg'
);

// Outgoing
Telecom.startCall(uuid, '+15557654321', 'Grace Hopper', false, avatarUrl);

Telecom.setMuted(uuid, true);
Telecom.setAudioRoute(uuid, 'speaker'); // 'earpiece' | 'speaker' | 'bluetooth'
Telecom.sendDtmf(uuid, '123#');
Telecom.endCall(uuid);
```

---

## react-native-callkeep vs react-native-telecom

| | **react-native-telecom** | **react-native-callkeep** |
|--|--------------------------|---------------------------|
| Android API | Jetpack **Core-Telecom** | Legacy ConnectionService focus |
| Bridge | **TurboModule** / New Arch | Older bridge (painful on New Arch) |
| Native language | **Kotlin** | Java |
| Call UI | CallStyle + customizable full-screen | System / limited customization |
| Ringtone control | App player + silent CallStyle channel | Varies by OEM |
| Audio routes | Earpiece / speaker / Bluetooth API | Limited / app-side |
| Push wake helper | `TelecomPushBridge` | DIY |
| iOS | Coming later | CallKit today |
| Best for | New Arch VoIP apps (Telnyx, WebRTC…) | Legacy RN apps already on CallKeep |

If your search was *“react native callkeep new architecture”* or *“callkeep not working fabric”* — start here.

---

## API reference

### Methods

| Method | Description |
|--------|-------------|
| `setup(options?)` | Register with Android Telecom (`CallsManager`) |
| `displayIncomingCall(uuid, handle, callerName, hasVideo?, autoAnswer?, avatarUrl?)` | Show incoming CallStyle + full-screen UI |
| `startCall(uuid, handle, callerName, hasVideo?, avatarUrl?)` | Start outgoing call UI |
| `answerCall(uuid)` | Answer (native or JS) |
| `endCall(uuid)` / `endAllCalls()` | Hang up |
| `setMuted(uuid, muted)` | Microphone mute |
| `setOnHold(uuid, hold)` | Hold / resume |
| `setAudioRoute(uuid, route)` | `earpiece` \| `speaker` \| `bluetooth` |
| `getAvailableAudioRoutes()` | List routes + availability |
| `sendDtmf(uuid, digits)` | Local DTMF + `dtmfTone` events |
| `handlePushMessage(data)` | Foreground FCM data → incoming call |
| `addEventListener` / `removeAllListeners` | Native events |

### Events

| Event | Payload | When |
|-------|---------|------|
| `didDisplayIncomingCall` | `{ callUUID, handle, callerName, hasVideo, direction, avatarUrl? }` | Call UI shown |
| `answerCall` | `{ callUUID }` | Answered |
| `endCall` | `{ callUUID, error? }` | Ended |
| `didChangeMuteState` | `{ callUUID, muted }` | Mute toggled |
| `didChangeHoldState` | `{ callUUID, hold }` | Hold toggled |
| `didChangeSpeaker` | `{ callUUID, speakerOn }` | Legacy speaker flag |
| `didChangeAudioRoute` | `{ callUUID, route }` | Route changed |
| `didActivateAudioSession` | `{ callUUID, route }` | **Attach media here** |
| `didDeactivateAudioSession` | `{ callUUID? }` | **Detach media here** |
| `dtmfTone` | `{ callUUID, digit }` | Keypad / `sendDtmf` |

---

## FCM / push (background & killed)

When the app is killed, JS may not run. Use native:

```kotlin
// FirebaseMessagingService.onMessageReceived
TelecomPushBridge.handleIncomingCall(applicationContext, message.data)
```

Payload example:

```json
{
  "type": "incoming_call",
  "uuid": "in-1710000000",
  "handle": "+15551234567",
  "callerName": "Ada Lovelace",
  "avatarUrl": "https://example.com/ada.jpg"
}
```

Details: [docs/fcm.md](./docs/fcm.md)

---

## Media: Telnyx, WebRTC, LiveKit, Twilio

This library is **UI + Telecom**, not a media engine.

1. Show native call UI with `displayIncomingCall` / `startCall`
2. On `didActivateAudioSession` → connect Telnyx / WebRTC / LiveKit
3. On `didDeactivateAudioSession` → hang up media
4. Sync mute with `didChangeMuteState`
5. Forward `dtmfTone` into your SIP / WebRTC DTMF API

Guide: [docs/media.md](./docs/media.md)

---

## Customize the call screens

Override in your app:

- `android/app/src/main/res/layout/rn_telecom_incoming_call.xml`
- `android/app/src/main/res/layout/rn_telecom_incall.xml`

Or theme via `Telecom.setup({ ui: { … } })`.

Keep the documented view ids if you override XML.

---

## Example app

```bash
git clone https://github.com/itxmoizdev/react-native-telecom.git
cd react-native-telecom
yarn
yarn prepare
yarn example android
```

Use a **physical Android device**. Emulators are unreliable for Telecom / CallStyle / full-screen intent.

---

## Documentation

| Doc | Topic |
|-----|--------|
| [docs/android-setup.md](./docs/android-setup.md) | Permissions, OEM, CallStyle, minSdk |
| [docs/fcm.md](./docs/fcm.md) | FCM high-priority incoming wake |
| [docs/media.md](./docs/media.md) | WebRTC / Telnyx audio session wiring |

---

## FAQ (AEO — answers search engines & assistants extract)

### Is this a react-native-callkeep alternative?
**Yes.** It targets the same job (native VoIP call UI) with Jetpack Core-Telecom and TurboModules for New Architecture.

### Does it support iOS CallKit?
**Not in v0.x.** Android is production-focused first; CallKit is planned.

### Can I use it with Telnyx / WebRTC?
**Yes.** Use audio-session events to attach your media SDK. The library does not ship Telnyx or WebRTC binaries.

### Does it show a native Android call screen?
It shows **system CallStyle notifications** plus a **Pixel-style full-screen Activity** you can brand. Third-party apps cannot open the stock Dialer UI.

### New Architecture required?
**Yes** — this is a TurboModule library.

### minSdk?
**26** (Android 8.0).

---

## Roadmap

- [x] Android Core-Telecom + CallStyle + full-screen UI  
- [x] Ringtone / vibrate / mute / hold / DTMF events / audio routes  
- [x] Avatar URL + FCM push bridge + media session events  
- [ ] iOS CallKit  
- [ ] Deeper Telecom audio endpoint APIs  
- [ ] Published examples: Telnyx + LiveKit  

---

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Issues and PRs welcome — especially OEM test reports (Vivo, Xiaomi, Oppo, Samsung).

## License

MIT © [Abdul Moiz](https://github.com/itxmoizdev)

---

### Discoverability

**npm package name:** `react-native-telecom`  
**GitHub:** https://github.com/itxmoizdev/react-native-telecom  

If you found this while searching for **react-native-callkeep**, **CallKeep New Architecture**, **React Native VoIP incoming call notification**, or **Android Core-Telecom React Native**, star the repo and open an issue with your stack (Telnyx, WebRTC, Expo prebuild, etc.) so we can improve docs and examples.
