// src/pages/Recordings.tsx
import React, { useEffect, useState } from 'react';
import CallRecorder, { RecordingMeta } from '../plugins/call-recorder';
import { IonContent, IonPage } from '@ionic/react';

export default function Recordings() {
  const [records, setRecords] = useState<RecordingMeta[]>([]);

  useEffect(() => {
    fetchList();
  }, []);

  const fetchList = async () => {
    const r = await CallRecorder.getRecordings();
    setRecords(r.recordings || []);
  };

  const play = (path: string) => {
    const audio = new Audio(path);
    audio.play();
  };

  return (
    <IonPage>
    <IonContent>
    <div className="p-6 max-w-xl mx-auto">
      <h1 className="text-3xl font-bold mb-4">Recordings</h1>
      {records.length === 0 ? (
        <div>No recordings found.</div>
      ) : (
        <ul className="space-y-3">
          {records.map((rec) => (
            <li key={rec.id} className="p-3 border rounded flex items-center justify-between">
              <div>
                <div className="font-medium">{rec.name}</div>
                <div className="text-sm text-gray-500">{new Date(rec.createdAt).toLocaleString()}</div>
                <div className="text-xs text-gray-400">Method: {rec.method}</div>
              </div>
              <div className="flex items-center space-x-2">
                <button onClick={() => play(rec.path)} className="px-3 py-1 border rounded">Play</button>
                <a href={rec.path} download className="px-3 py-1 border rounded">Download</a>
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4">
        <button onClick={fetchList} className="px-4 py-2 border rounded">Refresh</button>
      </div>
    </div>
    </IonContent>
    </IonPage>
  );
}
