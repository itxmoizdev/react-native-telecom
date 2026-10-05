# FCM / push wake (Android)

Incoming calls when the app is **background or killed** need a high-priority **data** FCM message that starts Core-Telecom + CallStyle UI **natively** (JS may not be running yet).

## Payload (data message)

```json
{
  "type": "incoming_call",
  "uuid": "in-1710000000",
  "handle": "+15551234567",
  "callerName": "Ada Lovelace",
  "hasVideo": "false",
  "autoAnswer": "false",
  "avatarUrl": "https://example.com/ada.jpg"
}
```

Aliases: `callUUID`, `number`, `name`, `avatar`.

Send as **data-only** (or data + notification), priority **high**, with Android high priority.

## Native (required for killed state)

In your app `FirebaseMessagingService`:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
  val data = message.data
  if (TelecomPushBridge.handleIncomingCall(applicationContext, data)) {
    return
  }
  // other push types…
}
```

`TelecomPushBridge` lives in `com.telecom` — no Firebase dependency inside the library. Your app adds Firebase + `google-services.json`.

## Foreground JS

```ts
import messaging from '@react-native-firebase/messaging';
import Telecom from 'react-native-telecom';

messaging().onMessage(async (remoteMessage) => {
  await Telecom.handlePushMessage(remoteMessage.data ?? {});
});

messaging().setBackgroundMessageHandler(async (remoteMessage) => {
  // Prefer native TelecomPushBridge for reliability when killed.
  await Telecom.handlePushMessage(remoteMessage.data ?? {});
});
```

## Checklist

- [ ] FCM data high priority
- [ ] `TelecomPushBridge` in MessagingService
- [ ] Notification + full-screen intent permissions
- [ ] OEM autostart / battery unrestricted
- [ ] Test: force-stop app → send push → ring + full-screen
