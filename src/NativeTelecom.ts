import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type TelecomSetupOptions = {
  appName?: string;
  supportsVideo?: boolean;
  /** Customize the full-screen incoming host Activity (not the CallStyle notification). */
  ui?: {
    incomingTitle?: string;
    answerLabel?: string;
    declineLabel?: string;
    /** Hex colors e.g. `#000000`, `#43A047` */
    backgroundColor?: string;
    titleColor?: string;
    nameColor?: string;
    handleColor?: string;
    answerColor?: string;
    declineColor?: string;
    /** Default true. Set false for notification-only (native CallStyle). */
    launchFullScreenOnIncoming?: boolean;
  };
};

export type TelecomAudioRoute = 'earpiece' | 'speaker' | 'bluetooth';

export interface Spec extends TurboModule {
  setup(options: Object): Promise<boolean>;
  displayIncomingCall(
    uuid: string,
    handle: string,
    callerName: string,
    hasVideo: boolean,
    autoAnswer: boolean,
    avatarUrl: string
  ): void;
  startCall(
    uuid: string,
    handle: string,
    callerName: string,
    hasVideo: boolean,
    avatarUrl: string
  ): void;
  answerCall(uuid: string): void;
  endCall(uuid: string): void;
  endAllCalls(): void;
  setMuted(uuid: string, muted: boolean): void;
  setOnHold(uuid: string, hold: boolean): void;
  setAudioRoute(uuid: string, route: string): void;
  getAvailableAudioRoutes(): Promise<Object>;
  sendDtmf(uuid: string, digits: string): void;
  handlePushMessage(data: Object): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Telecom');
