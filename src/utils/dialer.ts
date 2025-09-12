import { registerPlugin } from '@capacitor/core';

export interface DialerPlugin {
  requestDefaultDialer(): Promise<void>;
  makeCall(options: { number: string }): Promise<void>;
  registerPhoneAccount(): Promise<void>;
  addListener(
    eventName: 'callIncoming' | 'callAnswered' | 'callEnded',
    listenerFunc: (info: any) => void
  ): Promise<{ remove: () => void }>;
}

const Dialer = registerPlugin<DialerPlugin>('DialerPlugin');

export default Dialer;

