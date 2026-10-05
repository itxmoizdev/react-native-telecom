import { useCallback, useEffect, useState } from 'react';
import {
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import Telecom, {
  type TelecomEventName,
  type TelecomEvents,
} from 'react-native-telecom';

function createCallId(prefix: string): string {
  return `${prefix}-${Date.now()}`;
}

export default function App() {
  const [ready, setReady] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [activeCallId, setActiveCallId] = useState<string | null>(null);
  const [muted, setMuted] = useState(false);
  const [onHold, setOnHold] = useState(false);
  const [autoAnswer, setAutoAnswer] = useState(false);

  const log = useCallback((message: string) => {
    console.log(`[TelecomExample] ${message}`);
    setLogs((prev) => [
      `${new Date().toLocaleTimeString()}  ${message}`,
      ...prev,
    ]);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function boot() {
      if (Platform.OS !== 'android') {
        log('iOS is not supported in v0.x');
        return;
      }

      try {
        if (Platform.Version >= 33) {
          await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
          );
        }

        await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
        );

        const accepted = await Telecom.setup({
          appName: 'Telecom Example',
          supportsVideo: true,
          ui: {
            incomingTitle: 'Incoming call',
            answerLabel: 'Answer',
            declineLabel: 'Decline',
            backgroundColor: '#F7F4EF',
            nameColor: '#1C1B1F',
            handleColor: '#49454F',
            answerColor: '#1B5E20',
            declineColor: '#1C1B1F',
            launchFullScreenOnIncoming: true,
          },
        });

        if (!cancelled) {
          setReady(accepted);
          log(`setup() => ${accepted}`);
          log(
            'Incoming uses native CallStyle notification (system Answer/Decline UI).'
          );
        }
      } catch (error) {
        log(`setup failed: ${String(error)}`);
      }
    }

    boot();

    const events: TelecomEventName[] = [
      'answerCall',
      'endCall',
      'didDisplayIncomingCall',
      'didChangeHoldState',
      'didChangeMuteState',
      'didChangeAudioRoute',
      'didActivateAudioSession',
      'didDeactivateAudioSession',
      'dtmfTone',
    ];

    const subscriptions = events.map((eventName) =>
      Telecom.addEventListener(eventName, (payload) => {
        log(`${eventName}: ${JSON.stringify(payload)}`);

        if (eventName === 'didDisplayIncomingCall') {
          const data = payload as TelecomEvents['didDisplayIncomingCall'];
          setActiveCallId(data.callUUID);
          setMuted(false);
          setOnHold(false);
        }

        if (eventName === 'answerCall') {
          const data = payload as TelecomEvents['answerCall'];
          setActiveCallId(data.callUUID);
        }

        if (eventName === 'endCall') {
          const data = payload as TelecomEvents['endCall'];
          setActiveCallId((current) =>
            current === data.callUUID ? null : current
          );
          setMuted(false);
          setOnHold(false);
        }

        if (eventName === 'didChangeMuteState') {
          const data = payload as TelecomEvents['didChangeMuteState'];
          setMuted(data.muted);
        }

        if (eventName === 'didChangeHoldState') {
          const data = payload as TelecomEvents['didChangeHoldState'];
          setOnHold(data.hold);
        }
      })
    );

    return () => {
      cancelled = true;
      subscriptions.forEach((subscription) => subscription.remove());
    };
  }, [log]);

  const demoAvatar =
    'https://i.pravatar.cc/300?u=react-native-telecom';

  const onIncoming = () => {
    const uuid = createCallId('in');
    setActiveCallId(uuid);
    Telecom.displayIncomingCall(
      uuid,
      '+15551234567',
      'Ada Lovelace',
      false,
      autoAnswer,
      demoAvatar
    );
    log(`displayIncomingCall(${uuid}, autoAnswer=${autoAnswer})`);
  };

  const onOutgoing = () => {
    const uuid = createCallId('out');
    setActiveCallId(uuid);
    setMuted(false);
    setOnHold(false);
    Telecom.startCall(
      uuid,
      '+15557654321',
      'Grace Hopper',
      false,
      demoAvatar
    );
    log(`startCall(${uuid})`);
  };

  const onAnswer = () => {
    if (!activeCallId) {
      log('answer ignored — no active call');
      return;
    }
    Telecom.answerCall(activeCallId);
    log(`answerCall(${activeCallId})`);
  };

  const onEnd = () => {
    if (!activeCallId) {
      return;
    }
    const uuid = activeCallId;
    Telecom.endCall(uuid);
    setActiveCallId(null);
    setMuted(false);
    setOnHold(false);
    log(`endCall(${uuid})`);
  };

  const onToggleMute = () => {
    if (!activeCallId) {
      log('mute ignored — no active call');
      return;
    }
    const next = !muted;
    Telecom.setMuted(activeCallId, next);
    log(`setMuted(${activeCallId}, ${next})`);
  };

  const onToggleHold = () => {
    if (!activeCallId) {
      log('hold ignored — no active call');
      return;
    }
    const next = !onHold;
    Telecom.setOnHold(activeCallId, next);
    log(`setOnHold(${activeCallId}, ${next})`);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>react-native-telecom</Text>
      <Text style={styles.subtitle}>
        Native CallStyle notification + Core-Telecom
      </Text>
      <Text style={styles.status}>
        Status: {ready ? 'ready' : 'not ready'}
        {activeCallId ? ` · call ${activeCallId}` : ''}
      </Text>

      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>Auto-answer incoming</Text>
        <Switch value={autoAnswer} onValueChange={setAutoAnswer} />
      </View>

      <View style={styles.row}>
        <Action label="Incoming" onPress={onIncoming} disabled={!ready} />
        <Action label="Outgoing" onPress={onOutgoing} disabled={!ready} />
      </View>
      <View style={styles.row}>
        <Action label="Answer" onPress={onAnswer} disabled={!activeCallId} />
        <Action
          label={muted ? 'Unmute' : 'Mute'}
          onPress={onToggleMute}
          disabled={!activeCallId}
        />
        <Action
          label={onHold ? 'Resume' : 'Hold'}
          onPress={onToggleHold}
          disabled={!activeCallId}
        />
        <Action label="End" onPress={onEnd} disabled={!activeCallId} />
      </View>

      <Text style={styles.logTitle}>Events</Text>
      <ScrollView style={styles.logBox}>
        {logs.map((line, index) => (
          <Text key={`${index}-${line}`} style={styles.logLine}>
            {line}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}

function Action({
  label,
  onPress,
  disabled,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        disabled && styles.buttonDisabled,
        pressed && !disabled && styles.buttonPressed,
      ]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 64,
    paddingHorizontal: 20,
    backgroundColor: '#0f172a',
  },
  title: {
    color: '#f8fafc',
    fontSize: 24,
    fontWeight: '700',
  },
  subtitle: {
    color: '#94a3b8',
    marginTop: 6,
    marginBottom: 16,
  },
  status: {
    color: '#38bdf8',
    marginBottom: 12,
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  switchLabel: {
    color: '#e2e8f0',
    fontSize: 15,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 10,
  },
  button: {
    flex: 1,
    backgroundColor: '#2563eb',
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonPressed: {
    opacity: 0.85,
  },
  buttonDisabled: {
    backgroundColor: '#334155',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 12,
  },
  logTitle: {
    color: '#e2e8f0',
    marginTop: 18,
    marginBottom: 8,
    fontWeight: '600',
  },
  logBox: {
    flex: 1,
    backgroundColor: '#1e293b',
    borderRadius: 12,
    padding: 12,
    marginBottom: 24,
  },
  logLine: {
    color: '#cbd5e1',
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace' }),
    fontSize: 12,
    marginBottom: 6,
  },
});
