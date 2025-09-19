// CallHistory.tsx
import React, { useState, useEffect } from 'react';
import {
  IonPage,
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonButtons,
  IonButton,
  IonBackButton,
  IonLoading,
  IonSearchbar,
  IonCard,
  IonCardContent,
  IonIcon,
  IonText,
  IonChip,
} from '@ionic/react';
import { call, callOutline, closeCircle, time, search } from 'ionicons/icons';
import CallHistory, { CallHistoryEntry } from '../plugins/call-history';
import './CallHistory.css'; // We'll create this CSS file for custom styles

const CallHistoryPage: React.FC = () => {
  const [calls, setCalls] = useState<CallHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    loadCallHistory();
  }, []);

  const loadCallHistory = async () => {
    setLoading(true);
    setError('');
    
    try {
      // Check if plugin is available
      if (typeof CallHistory.getCallHistory !== 'function') {
        throw new Error('Call history feature is not available on this device');
      }
      
      const result = await CallHistory.getCallHistory({ limit: 50 });
      if (result.calls && result.calls.length > 0) {
        setCalls(result.calls);
      } else {
        // Show dummy data if no call history found
        setCalls(generateDummyData());
      }
    } catch (err: any) {
      console.error('Error loading call history:', err);
      setError(err.message || 'Failed to load call history');
      // Show dummy data even on error for demo purposes
      setCalls(generateDummyData());
    } finally {
      setLoading(false);
    }
  };

  const generateDummyData = (): CallHistoryEntry[] => {
    const now = Date.now();
    return [
      {
        id: '1',
        name: 'John Doe',
        number: '+1 (555) 123-4567',
        type: 'INCOMING',
        date: now - 1000 * 60 * 5, // 5 minutes ago
        duration: 127,
      },
      {
        id: '2',
        name: 'Jane Smith',
        number: '+1 (555) 987-6543',
        type: 'OUTGOING',
        date: now - 1000 * 60 * 30, // 30 minutes ago
        duration: 45,
      },
      {
        id: '3',
        name: 'Mom',
        number: '+1 (555) 555-1234',
        type: 'MISSED',
        date: now - 1000 * 60 * 60 * 2, // 2 hours ago
        duration: 0,
      },
      {
        id: '4',
        name: 'Work',
        number: '+1 (555) 444-5678',
        type: 'INCOMING',
        date: now - 1000 * 60 * 60 * 5, // 5 hours ago
        duration: 312,
      },
      {
        id: '5',
        name: 'Unknown',
        number: 'Private Number',
        type: 'MISSED',
        date: now - 1000 * 60 * 60 * 24, // 1 day ago
        duration: 0,
      },
    ];
  };

  const formatDate = (timestamp: number) => {
    const now = new Date();
    const date = new Date(timestamp);
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);
    
    if (diffMins < 60) {
      return `${diffMins} min ago`;
    } else if (diffHours < 24) {
      return `${diffHours} hr ago`;
    } else if (diffDays < 7) {
      return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
    } else {
      return date.toLocaleDateString();
    }
  };

  const formatDuration = (seconds: number) => {
    if (seconds === 0) return '';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const getCallIcon = (type: string) => {
    switch (type) {
      case 'INCOMING': return call;
      case 'OUTGOING': return callOutline;
      case 'MISSED': return closeCircle;
      default: return callOutline;
    }
  };

  const getCallColor = (type: string) => {
    switch (type) {
      case 'MISSED': return 'danger';
      case 'INCOMING': return 'success';
      case 'OUTGOING': return 'primary';
      default: return 'medium';
    }
  };

  const getCallTypeText = (type: string) => {
    switch (type) {
      case 'INCOMING': return 'Incoming';
      case 'OUTGOING': return 'Outgoing';
      case 'MISSED': return 'Missed';
      default: return type;
    }
  };

  const filteredCalls = calls.filter(c =>
    c.name?.toLowerCase().includes(searchText.toLowerCase()) ||
    c.number?.toLowerCase().includes(searchText.toLowerCase())
  );

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/home" />
          </IonButtons>
          <IonTitle>Call History</IonTitle>
          <IonButtons slot="end">
            <IonButton onClick={loadCallHistory}>
              Refresh
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <IonLoading isOpen={loading} message="Loading call history..." />
        
        <div className="search-container">
          <IonSearchbar
            value={searchText}
            onIonInput={(e) => setSearchText(e.detail.value!)}
            placeholder="Search calls"
            className="call-searchbar"
          />
        </div>

        {error && (
          <div className="error-container">
            <IonText color="danger">
              <p>{error}</p>
            </IonText>
            <IonButton onClick={loadCallHistory}>Try Again</IonButton>
          </div>
        )}

        <div className="calls-list">
          {filteredCalls.map((call, index) => (
            <IonCard key={index} className="call-card">
              <IonCardContent>
                <div className="call-header">
                  <IonIcon 
                    icon={getCallIcon(call.type)} 
                    color={getCallColor(call.type)}
                    className="call-type-icon" 
                  />
                  <div className="call-info">
                    <IonText className="call-name">
                      {call.name || call.number}
                    </IonText>
                    {call.name && call.name !== call.number && (
                      <IonText color="medium" className="call-number">
                        {call.number}
                      </IonText>
                    )}
                  </div>
                  <div className="call-time">
                    <IonText color="medium">
                      {formatDate(call.date)}
                    </IonText>
                  </div>
                </div>
                
                <div className="call-footer">
                  <IonChip color={getCallColor(call.type)} className="call-type-chip">
                    {getCallTypeText(call.type)}
                  </IonChip>
                  
                  {call.duration > 0 && (
                    <div className="call-duration">
                      <IonIcon icon={time} color="medium" />
                      <IonText color="medium">
                        {formatDuration(call.duration)}
                      </IonText>
                    </div>
                  )}
                </div>
              </IonCardContent>
            </IonCard>
          ))}
        </div>

        {filteredCalls.length === 0 && !loading && (
          <div className="empty-state">
            <IonIcon icon={search} size="large" color="medium" />
            <IonText color="medium">
              <p>No calls found</p>
            </IonText>
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default CallHistoryPage;