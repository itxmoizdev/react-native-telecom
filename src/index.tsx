import {
  NativeEventEmitter,
  NativeModules,
  Platform,
  type EmitterSubscription,
} from 'react-native';
import NativeTelecom, {
  type TelecomAudioRoute,
  type TelecomSetupOptions,
} from './NativeTelecom';

export type { TelecomAudioRoute, TelecomSetupOptions };

export type TelecomAudioRouteInfo = {
  id: TelecomAudioRoute;
  name: string;
  available: boolean;
  selected: boolean;
};

export type TelecomEvents = {
  answerCall: { callUUID: string };
  endCall: { callUUID: string };
  didDisplayIncomingCall: {
    callUUID: string;
    handle: string;
    callerName: string;
    hasVideo: boolean;
    direction: 'incoming' | 'outgoing';
    avatarUrl?: string;
  };
  didChangeHoldState: { callUUID: string; hold: boolean };
  didChangeMuteState: { callUUID: string; muted: boolean };
  didChangeSpeaker: { callUUID: string; speakerOn: boolean };
  didChangeAudioRoute: { callUUID: string; route: TelecomAudioRoute };
  didActivateAudioSession: { callUUID: string; route: TelecomAudioRoute };
  didDeactivateAudioSession: { callUUID?: string };
  dtmfTone: { callUUID: string; digit: string };
};

export type TelecomEventName = keyof TelecomEvents;

export type TelecomPushPayload = {
  uuid?: string;
  callUUID?: string;
  handle?: string;
  number?: string;
  callerName?: string;
  name?: string;
  hasVideo?: boolean | string;
  autoAnswer?: boolean | string;
  avatarUrl?: string;
  avatar?: string;
  type?: string;
  [key: string]: unknown;
};

const isAndroid = Platform.OS === 'android';

const emitter = new NativeEventEmitter(
  (NativeModules.Telecom ?? NativeTelecom) as never
);

function assertAndroid(): void {
  if (!isAndroid) {
    throw new Error(
      'react-native-telecom: Android Jetpack Core-Telecom only in v0.x. iOS CallKit support is coming later.'
    );
  }
}

export async function setup(
  options: TelecomSetupOptions = {}
): Promise<boolean> {
  assertAndroid();
  return NativeTelecom.setup({
    appName: options.appName ?? 'App',
    supportsVideo: options.supportsVideo ?? true,
    ui: options.ui,
  });
}

export function displayIncomingCall(
  uuid: string,
  handle: string,
  callerName: string,
  hasVideo: boolean = false,
  autoAnswer: boolean = false,
  avatarUrl: string = ''
): void {
  assertAndroid();
  NativeTelecom.displayIncomingCall(
    uuid,
    handle,
    callerName,
    hasVideo,
    autoAnswer,
    avatarUrl
  );
}

export function startCall(
  uuid: string,
  handle: string,
  callerName: string,
  hasVideo: boolean = false,
  avatarUrl: string = ''
): void {
  assertAndroid();
  NativeTelecom.startCall(uuid, handle, callerName, hasVideo, avatarUrl);
}

export function answerCall(uuid: string): void {
  assertAndroid();
  NativeTelecom.answerCall(uuid);
}

export function endCall(uuid: string): void {
  assertAndroid();
  NativeTelecom.endCall(uuid);
}

export function endAllCalls(): void {
  assertAndroid();
  NativeTelecom.endAllCalls();
}

export function setMuted(uuid: string, muted: boolean): void {
  assertAndroid();
  NativeTelecom.setMuted(uuid, muted);
}

export function setOnHold(uuid: string, hold: boolean): void {
  assertAndroid();
  NativeTelecom.setOnHold(uuid, hold);
}

export function setAudioRoute(
  uuid: string,
  route: TelecomAudioRoute
): void {
  assertAndroid();
  NativeTelecom.setAudioRoute(uuid, route);
}

export async function getAvailableAudioRoutes(): Promise<
  TelecomAudioRouteInfo[]
> {
  assertAndroid();
  const routes = await NativeTelecom.getAvailableAudioRoutes();
  return routes as TelecomAudioRouteInfo[];
}

/** Local DTMF beep + `dtmfTone` event — forward digits in your signaling/WebRTC. */
export function sendDtmf(uuid: string, digits: string): void {
  assertAndroid();
  NativeTelecom.sendDtmf(uuid, digits);
}

/**
 * Handle an FCM / push data map from JS (foreground).
 * For killed-state, call `TelecomPushBridge` from your native MessagingService.
 */
export async function handlePushMessage(
  data: TelecomPushPayload
): Promise<boolean> {
  assertAndroid();
  return NativeTelecom.handlePushMessage(data);
}

export function addEventListener<E extends TelecomEventName>(
  eventName: E,
  listener: (event: TelecomEvents[E]) => void
): EmitterSubscription {
  assertAndroid();
  return emitter.addListener(eventName, (event) => {
    listener(event as TelecomEvents[E]);
  });
}

export function removeAllListeners(eventName?: TelecomEventName): void {
  if (eventName) {
    emitter.removeAllListeners(eventName);
    return;
  }

  (
    [
      'answerCall',
      'endCall',
      'didDisplayIncomingCall',
      'didChangeHoldState',
      'didChangeMuteState',
      'didChangeSpeaker',
      'didChangeAudioRoute',
      'didActivateAudioSession',
      'didDeactivateAudioSession',
      'dtmfTone',
    ] as TelecomEventName[]
  ).forEach((name) => emitter.removeAllListeners(name));
}

const Telecom = {
  setup,
  displayIncomingCall,
  startCall,
  answerCall,
  endCall,
  endAllCalls,
  setMuted,
  setOnHold,
  setAudioRoute,
  getAvailableAudioRoutes,
  sendDtmf,
  handlePushMessage,
  addEventListener,
  removeAllListeners,
};

export default Telecom;
