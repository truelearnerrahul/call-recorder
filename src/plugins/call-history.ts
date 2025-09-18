import { registerPlugin } from '@capacitor/core';

export interface CallHistoryEntry {
  id: string;
  number: string;
  date: number;
  duration: number;
  type: string;
  name: string;
}

export interface CallHistoryPlugin {
  getCallHistory(options: { limit?: number }): Promise<{ calls: CallHistoryEntry[] }>;
}

const CallHistory = registerPlugin<CallHistoryPlugin>('CallHistory');

export default CallHistory;