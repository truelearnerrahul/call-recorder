import React, { useEffect, useState } from 'react';
import {
  IonButton,
  IonCard,
  IonCardHeader,
  IonCardTitle,
  IonCardContent,
  IonText,
  IonPage,
  IonContent,
  IonHeader,
  IonToolbar,
  IonTitle,
} from '@ionic/react';
import { Capacitor } from '@capacitor/core';
import { PhoneCall } from 'lucide-react';
import Dialer from '../utils/dialer';

const Home: React.FC = () => {
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [status, setStatus] = useState('idle');
  const [isDefaultDialer, setIsDefaultDialer] = useState(false);

  useEffect(() => {
    async function checkDefaultDialer() {
      try {
        // @ts-ignore
        const res = await (window as any).AndroidBridge?.isDefaultDialer();
        setIsDefaultDialer(!!res?.granted);
      } catch (e) {
        console.warn('Error checking default dialer', e);
      }
    }
    checkDefaultDialer();
  }, []);

  React.useEffect(() => {
    function onRes(e: any) {
      const obj = e.detail;
      if (obj?.granted) {
        setPermissionsGranted(true);
        setStatus('permissions granted');
      } else {
        setStatus('permissions denied');
      }
    }
    window.addEventListener('androidPermissionsResult', onRes);
    return () => {
      window.removeEventListener('androidPermissionsResult', onRes);
    };
  }, []);

  useEffect(() => {
    if ((window as any).AndroidBridge) {
      (window as any).AndroidBridge.requestPermissions();
    }
  }, []);

  (window as any)._androidDialerResult = (res: { granted: boolean }) => {
    console.log('Default dialer accepted?', res.granted);
    if (res.granted) setIsDefaultDialer(true);
  };

  (window as any)._androidPermissionsResult = (res: { granted: boolean }) => {
    if (res?.granted) {
      setPermissionsGranted(true);
      setStatus('permissions granted');
    } else {
      setStatus('permissions denied');
    }
    console.log('Permissions granted?', res.granted);
  };

  const requestPermissions = async () => {
    setStatus('requesting permissions...');
    try {
      if (Capacitor.getPlatform() !== 'android') {
        setStatus('Only Android supported for call recording.');
        return;
      }
      // @ts-ignore
      await (window as any).AndroidBridge?.requestPermissions();
    } catch (e) {
      console.error(e);
      setStatus('error requesting permissions');
    }
  };

  const requestDefaultDialer = async () => {
    setStatus('requesting default dialer role...');
    try {
      // @ts-ignore
      await (window as any).AndroidBridge?.requestDefaultDialer();
      setStatus('requested default dialer; user will see a system prompt');
    } catch (e) {
      setStatus('error requesting default dialer');
    }
  };

  const startDemoDial = () => {
    window.open('tel:+1234567890');
  };

  useEffect(() => {
    const incoming = Dialer.addListener('callIncoming', (info) => {
      console.log('incoming:', info);
    });
    const answered = Dialer.addListener('callAnswered', (info) => {
      console.log('answered:', info);
    });
    const ended = Dialer.addListener('callEnded', (info) => {
      console.log('ended:', info);
    });

    return () => {
      incoming.then((h) => h.remove());
      answered.then((h) => h.remove());
      ended.then((h) => h.remove());
    };
  }, []);

  return (
    <IonPage>
      <IonContent>
        <IonHeader>
          <IonToolbar>
          </IonToolbar>
        </IonHeader>
        
        <IonCard className="w-full max-w-lg shadow-xl rounded-2xl bg-white dark:bg-gray-800">
            <IonCardHeader>
              <IonCardTitle className="text-xl font-semibold text-gray-800 dark:text-gray-100">
                Call Recorder MVP
              </IonCardTitle>
            </IonCardHeader>
            <IonCardContent>
              <IonText className="text-gray-600 dark:text-gray-300">
                This app will attempt to detect calls and start a native
                recorder. Behavior varies by device.
              </IonText>

              <div className="mt-6 flex flex-wrap gap-3">
                <IonButton
                  shape="round"
                  className="flex-1"
                  onClick={requestPermissions}
                >
                  Request Permissions
                </IonButton>
                <IonButton
                  shape="round"
                  className="flex-1"
                  onClick={startDemoDial}
                  color="secondary"
                >
                  Open Demo Dial
                </IonButton>
                <IonButton
                shape="round"
                className="flex-1"
                color="secondary"
                onClick={requestDefaultDialer}
              >
                Set as Default
              </IonButton>
                <IonButton
                shape="round"
                className="flex-1"
                color="secondary"
                routerLink='/recordings'
              >
               Call Recordings
              </IonButton>
              </div>

              <div className="mt-6 text-sm text-gray-700 dark:text-gray-300">
                <strong>Status:</strong> {status}
              </div>
            </IonCardContent>
          </IonCard>
      </IonContent>
    </IonPage>
  );
};

export default Home;
