import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  IonPage,
  IonContent,
  IonList,
  IonItem,
  IonLabel,
  IonButton,
  IonHeader,
  IonToolbar,
  IonTitle,
  IonRange,
  IonButtons,
  IonIcon,
  IonText
} from '@ionic/react';
import { play as playIcon, pause as pauseIcon } from 'ionicons/icons';
import { Filesystem, Directory } from '@capacitor/filesystem';

type Recording = {
  name: string;
  uri?: string;        // resolved file URI if available
  mime?: string;       // optional MIME type
};

const formatTime = (sec: number) => {
  if (!isFinite(sec) || sec < 0) sec = 0;
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}`;
};

const RecordingsPage: React.FC = () => {
  const [recordings, setRecordings] = useState<Recording[]>([]);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState<number>(0);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Load recordings
  useEffect(() => {
    const loadRecordings = async () => {
      try {
        const result = await Filesystem.readdir({
          path: 'Music/CallRecords',
          directory: Directory.External
        });
        const mp4Files = result.files
          .map(f => ('name' in f ? f.name : (f as any))) // compat for different result shapes
          .filter((name: string) => name.endsWith('.mp4') || name.endsWith('.m4a') || name.endsWith('.aac') || name.endsWith('.mp3') || name.endsWith('.webm') || name.endsWith('.amr'));

        const withUri: Recording[] = [];
        for (const name of mp4Files) {
          try {
            const fileUri = await Filesystem.getUri({
              path: `Music/CallRecords/${name}`,
              directory: Directory.External
            });
            withUri.push({ name, uri: fileUri.uri });
          } catch {
            // Fallback: keep only name, will base64 read on play
            withUri.push({ name });
          }
        }
        setRecordings(withUri);
      } catch (err) {
        console.error('Error reading recordings', err);
      }
    };
    loadRecordings();
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.src = '';
        audioRef.current.load();
        audioRef.current = null;
      }
    };
  }, []);

  const attachAudioEvents = useCallback((audio: HTMLAudioElement) => {
    const onLoaded = () => {
      setDuration(audio.duration || 0);
    };
    const onTime = () => {
      setCurrentTime(audio.currentTime || 0);
    };
    const onEnded = () => {
      setIsPlaying(false);
      setCurrentTime(0);
    };
    audio.addEventListener('loadedmetadata', onLoaded);
    audio.addEventListener('timeupdate', onTime);
    audio.addEventListener('ended', onEnded);

    return () => {
      audio.removeEventListener('loadedmetadata', onLoaded);
      audio.removeEventListener('timeupdate', onTime);
      audio.removeEventListener('ended', onEnded);
    };
  }, []);

  const resolveAudioSrc = async (rec: Recording): Promise<string> => {
    const file = await Filesystem.readFile({
      path: `Music/CallRecords/${rec.name}`,
      directory: Directory.External
    });
  
    const ext = rec.name.split('.').pop()?.toLowerCase();
    const mime =
      ext === 'mp3' ? 'audio/mpeg' :
      ext === 'm4a' ? 'audio/mp4' :
      ext === 'aac' ? 'audio/aac' :
      ext === 'wav' ? 'audio/wav' :
      'audio/mp4';
  
    return `data:${mime};base64,${file.data}`;
  };
  

  const playIndex = async (index: number) => {
    const rec = recordings[index];
    if (!rec) return;
  
    // If same item, toggle play/pause
    if (activeIndex === index && audioRef.current) {
      if (isPlaying) {
        audioRef.current.pause();
        setIsPlaying(false);
      } else {
        try {
          await audioRef.current.play();
          setIsPlaying(true);
        } catch (err) {
          console.error('Resume play failed', err);
        }
      }
      return;
    }
  
    // Stop previous
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.src = '';
      audioRef.current.load();
      audioRef.current = null;
    }
  
    // 🔑 Always resolve to base64
    let src: string;
    try {
      src = await resolveAudioSrc(rec);
    } catch (err) {
      console.error('Failed resolving audio source', err);
      return;
    }
  
    const audio = new Audio(src);
    audioRef.current = audio;
    setActiveIndex(index);
    setCurrentTime(0);
    setDuration(0);
  
    const detach = attachAudioEvents(audio);
  
    try {
      await audio.play();
      setIsPlaying(true);
    } catch (err) {
      console.error('Audio play failed', err);
      setIsPlaying(false);
    }
  
    audio.addEventListener('emptied', detach, { once: true });
  };
  

  const pauseCurrent = () => {
    if (audioRef.current) {
      audioRef.current.pause();
      setIsPlaying(false);
    }
  };

  const onSeek = (value: number) => {
    if (!audioRef.current || !isFinite(duration) || duration <= 0) return;
    const newTime = Math.min(Math.max(value, 0), duration);
    audioRef.current.currentTime = newTime;
    setCurrentTime(newTime);
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
        </IonToolbar>
      </IonHeader>
      <IonContent className="ion-padding">
        <IonList inset={true}>
          {recordings.map((rec, idx) => {
            const isActive = idx === activeIndex;
            const left = isActive ? formatTime(currentTime) : '00:00';
            const right = isActive ? `-${formatTime((duration || 0) - (currentTime || 0))}` : '--:--';
            return (
              <IonItem key={rec.name} lines="full">
                <IonLabel>
                  <h2>{rec.name}</h2>
                  <p>{rec.uri ? 'Local file (URI)' : 'Local file (base64 fallback)'}</p>
                  {isActive && (
                    <div style={{ marginTop: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <IonText>{left}</IonText>
                        <IonRange
                          aria-label="Seek"
                          min={0}
                          max={Math.max(1, duration || 1)}
                          step={1}
                          value={currentTime}
                          onIonChange={(e) => onSeek(Number(e.detail.value))}
                          style={{ flex: 1 }}
                        />
                        <IonText>{right}</IonText>
                      </div>
                    </div>
                  )}
                </IonLabel>
                <IonButtons slot="end">
                  {!isActive || !isPlaying ? (
                    <IonButton fill="clear" onClick={() => playIndex(idx)}>
                      <IonIcon icon={playIcon} slot="icon-only" />
                    </IonButton>
                  ) : (
                    <IonButton fill="clear" onClick={pauseCurrent}>
                      <IonIcon icon={pauseIcon} slot="icon-only" />
                    </IonButton>
                  )}
                </IonButtons>
              </IonItem>
            );
          })}
        </IonList>
      </IonContent>
    </IonPage>
  );
};

export default RecordingsPage;
