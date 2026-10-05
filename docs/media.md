# Media / WebRTC wiring (Android)

`react-native-telecom` owns **Telecom / UI / ringtone / audio route / mute**.  
It does **not** embed a media stack. Attach WebRTC, LiveKit, Twilio, etc. in JS when the audio session activates.

## Lifecycle

| Event | When | Your job |
|-------|------|----------|
| `didActivateAudioSession` | Outgoing becomes active, or incoming answered | Create/attach peer connection, enable mic/speaker tracks |
| `didDeactivateAudioSession` | Call ended | Close PC, release tracks |
| `didChangeMuteState` | Mute toggled (native or JS) | Mute/unmute local audio track |
| `didChangeAudioRoute` | earpiece / speaker / bluetooth | Optional; native already switches `AudioManager` |
| `dtmfTone` | Keypad / `sendDtmf` | `RTCRtpSender.dtmf.insertDTMF(digit)` or signaling |

## Example (WebRTC)

```ts
Telecom.addEventListener('didActivateAudioSession', async ({ callUUID }) => {
  await startWebRtcForCall(callUUID); // your code
});

Telecom.addEventListener('didChangeMuteState', ({ muted }) => {
  localAudioTrack.enabled = !muted;
});

Telecom.addEventListener('dtmfTone', ({ digit }) => {
  dtmfSender?.insertDTMF(digit, 200, 50);
});

Telecom.addEventListener('didDeactivateAudioSession', async () => {
  await stopWebRtc();
});
```

## Audio routes

```ts
await Telecom.getAvailableAudioRoutes();
// [{ id: 'earpiece', name: 'Phone', available, selected }, ...]

Telecom.setAudioRoute(callUUID, 'speaker');
Telecom.setAudioRoute(callUUID, 'bluetooth'); // needs BLUETOOTH_CONNECT
```

In-call UI **Audio** button cycles Phone → Speaker → Bluetooth (if connected).

## Avatar

Pass HTTPS (or file) URL as the last argument:

```ts
Telecom.displayIncomingCall(uuid, handle, name, false, false, 'https://…/photo.jpg');
Telecom.startCall(uuid, handle, name, false, 'https://…/photo.jpg');
```

Falls back to initials when missing/failed.
