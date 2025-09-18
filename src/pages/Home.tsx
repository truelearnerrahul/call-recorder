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
  IonIcon,
  IonSpinner,
} from '@ionic/react';
import { Capacitor } from '@capacitor/core';
import { warning } from 'ionicons/icons';

const Home: React.FC = () => {
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [status, setStatus] = useState('checking...');
  const [isDefaultDialer, setIsDefaultDialer] = useState(false);

  useEffect(() => {
    async function initChecks() {
      try {
        // Check if app is default dialer
        // @ts-ignore
        await (window as any).AndroidBridge?.isDefaultDialer();

        // Request permissions on load
        if ((window as any).AndroidBridge) {
          // @ts-ignore
          await (window as any).AndroidBridge.requestPermissions();
        }
        setStatus('ready');
      } catch (e) {
        console.warn('Init checks failed', e);
        setStatus('error during checks');
      }
    }
    initChecks();
  }, []);

  // Listener for Android permission results
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

  // Callbacks from native side
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
        setStatus('Only Android supported.');
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

  const openAccessibilitySettings = async () => {
    setStatus('requesting accessibility settings...');
    try {
      // @ts-ignore
      await (window as any).AndroidBridge?.openAccessibilitySettings();
      setStatus('requested accessibility settings; user will see a system prompt');
    } catch (e) {
      setStatus('error opening accessibility settings');
    }
  };

  return (
    <IonPage>
      {/* <IonHeader translucent>
        <IonToolbar>
          <IonTitle>Call Recorder MVP</IonTitle>
        </IonToolbar>
      </IonHeader> */}

      <IonContent fullscreen className="ion-padding">
        {status === 'checking...' ? (
          <div className="flex justify-center items-center h-full">
            <IonSpinner name="crescent" />
          </div>
        ) : !isDefaultDialer || !permissionsGranted ? (
          <IonCard className="m-4 shadow-lg rounded-2xl">
            <IonCardHeader>
              <IonCardTitle className="flex items-center gap-2 text-red-600">
                <IonIcon icon={warning} />
                Setup Required
              </IonCardTitle>
            </IonCardHeader>
            <IonCardContent>
              <IonText>
                To use this app, please make it the <strong>default dialer</strong>
                and grant all required permissions.
              </IonText>
              <div className="mt-4 flex flex-col gap-3">
                {!isDefaultDialer && (
                  <IonButton expand="block" onClick={requestDefaultDialer}>
                    Set as Default Dialer
                  </IonButton>
                )}
                {!permissionsGranted && (
                  <IonButton expand="block" color="secondary" onClick={requestPermissions}>
                    Grant Permissions
                  </IonButton>
                )}
                <IonButton expand="block" fill="outline" onClick={openAccessibilitySettings}>
                  Open Accessibility Settings
                </IonButton>
              </div>
              <div className="mt-4 text-sm text-gray-600">
                <strong>Status:</strong> {status}
              </div>
            </IonCardContent>
          </IonCard>
        ) : (
          <>

            <div className='mt-10! text-2xl font-bold'>
              Home Page
            </div>
          </>
        )}
      </IonContent>
    </IonPage>
  );
};

export default Home;
