# Android setup

`react-native-telecom` v0.x is **Android-only** and uses Jetpack [Core-Telecom](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom).

## Requirements

| Item | Value |
|------|--------|
| minSdk | **26** (Android 8.0) |
| CallStyle native UI | **API 31+** (Android 12+) recommended |
| Architecture | New Architecture / TurboModules (RN 0.76+) |
| Device | **Physical device** |
| Dependency | `androidx.core:core-telecom:1.0.1` |

## Permissions

Merged from the library manifest (plus request at runtime where needed):

- `MANAGE_OWN_CALLS`
- `RECORD_AUDIO` (runtime)
- `MODIFY_AUDIO_SETTINGS`
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_PHONE_CALL`
- `POST_NOTIFICATIONS` (Android 13+, runtime)
- `USE_FULL_SCREEN_INTENT` (Android 14+: user may need Settings → Special app access)
- `VIBRATE`, `WAKE_LOCK`, `INTERNET`
- `BLUETOOTH_CONNECT` (Android 12+, runtime for BT audio route)

## OEM / lock-screen checklist (Vivo, Xiaomi, Oppo, …)

1. Grant **Notifications**
2. Grant **Full-screen intents** / display over lock screen (Android 14+)
3. Disable battery restrictions for the app (Autostart / Background activity)
4. Allow **Phone / Calls** related toggles if the OEM exposes them
5. Test with screen locked — incoming Activity uses `showWhenLocked` + `turnScreenOn`

## Native CallStyle + ringtone

Incoming uses silent CallStyle notification + app-driven `TelecomRingtonePlayer` (default ringtone + vibrate, respects ringer mode). On answer, ringtone stops and notification becomes quiet ongoing CallStyle.

## Screens

| Screen | Layout override |
|--------|-----------------|
| Incoming | `res/layout/rn_telecom_incoming_call.xml` |
| Outgoing / in-call | `res/layout/rn_telecom_incall.xml` |

Keep view ids stable if you override layouts.

## JS API (Android)

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

// Attach real media (WebRTC / LiveKit) when audio session activates
Telecom.addEventListener('didActivateAudioSession', ({ callUUID }) => {
  // start peer connection / subscribe tracks
});
Telecom.addEventListener('didDeactivateAudioSession', () => {
  // tear down media
});
Telecom.addEventListener('dtmfTone', ({ callUUID, digit }) => {
  // send digit over signaling / WebRTC insertDTMF
});

Telecom.displayIncomingCall(uuid, '+15551234567', 'Ada', false, false, avatarUrl);
Telecom.startCall(uuid, '+15557654321', 'Grace', false, avatarUrl);
Telecom.setAudioRoute(uuid, 'speaker'); // earpiece | speaker | bluetooth
Telecom.sendDtmf(uuid, '123#');
Telecom.endCall(uuid);
```

## Related docs

- [FCM / push wake](./fcm.md)
- [Media / WebRTC wiring](./media.md)

## iOS

Not implemented in v0.x (intentionally deferred).
