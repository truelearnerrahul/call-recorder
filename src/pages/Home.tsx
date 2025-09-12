import React, { useEffect, useState } from 'react';
import { IonButton, IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonText, IonPage, IonContent } from '@ionic/react';
import { Capacitor } from '@capacitor/core';
import Dialer from '../utils/dialer';

const Home: React.FC = () => {
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [status, setStatus] = useState('idle');

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
    return () => { window.removeEventListener('androidPermissionsResult', onRes); };
  }, []);

  
  useEffect(() => {
    // ask for permissions on app load
    if ((window as any).AndroidBridge) {
      (window as any).AndroidBridge.requestPermissions();
    }
  }, []);

  (window as any)._androidDialerResult = (res: { granted: boolean }) => {
    console.log("Default dialer accepted?", res.granted);
    alert("Default dialer accepted?" + res.granted);
  };
  
  // to handle callback
  ;(window as any)._androidPermissionsResult = (res: { granted: boolean }) => {
    // alert("Permissions granted?" + res.granted);
    if (res?.granted) {
      setPermissionsGranted(true);
      setStatus('permissions granted');
    } else {
      setStatus('permissions denied');
    }
    console.log("Permissions granted?", res.granted);
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
    // open dialer with a number (demonstrate being dialer)
    window.open('tel:+1234567890');
  };

  useEffect(() => {
    const incoming = Dialer.addListener("callIncoming", (info) => {
      console.log("incoming:", info);
    });

    const answered = Dialer.addListener("callAnswered", (info) => {
      console.log("answered:", info);
    });

    const ended = Dialer.addListener("callEnded", (info) => {
      console.log("ended:", info);
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
        <IonCard style={{ margin: 16 }}>
          <IonCardHeader>
            <IonCardTitle>Call Recorder MVP (Android)</IonCardTitle>
          </IonCardHeader>
          <IonCardContent>
            <IonText>
              This app will attempt to detect calls and start a native recorder. Behavior varies by device.
            </IonText>
            <div style={{ marginTop: 12 }}>
              <IonButton onClick={requestPermissions}>Request Permissions</IonButton>
              <IonButton onClick={requestDefaultDialer} style={{ marginLeft: 8 }}>Request Default Dialer</IonButton>
              <IonButton onClick={startDemoDial} style={{ marginLeft: 8 }}>Open Dialer (demo)</IonButton>
            </div>

            <div style={{ marginTop: 12 }}>
              <strong>Status:</strong> {status}
            </div>
          </IonCardContent>
        </IonCard>
      </IonContent>
    </IonPage>
  );
};

export default Home;
