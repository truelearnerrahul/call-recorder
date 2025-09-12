import { registerPlugin } from '@capacitor/core';

export interface RecordingMeta {
  id: string;
  name: string;
  path: string;
  createdAt: number;
  method?: 'playback+mic'|'mic-only'|'speaker';
}

export interface CallRecorderPlugin {
  requestPermissions(): Promise<{granted: boolean}>;
  startRecording(opts?: { filename?: string, auto?: boolean }): Promise<{ success: boolean, id?: string }>;
  stopRecording(id?: string): Promise<{ success: boolean, path?: string }>;
  setAutoRecord(enabled: boolean): Promise<{ success: boolean }>;
  isRecording(): Promise<{ recording: boolean }>;
  getRecordings(): Promise<{ recordings: RecordingMeta[] }>;
  listenCallState(): Promise<{ listening: boolean }>;
}

const CallRecorder = registerPlugin<CallRecorderPlugin>('CallRecorder');

export default CallRecorder;
