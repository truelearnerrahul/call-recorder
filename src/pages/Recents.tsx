// Recents.tsx
import React, { useEffect, useState } from "react";
import {
  IonPage,
  IonContent,
  IonListHeader,
  IonList,
  IonItem,
  IonAvatar,
  IonIcon,
  IonLabel,
  IonNote,
  IonLoading,
  IonSearchbar,
  IonText,
  IonChip,
  IonButton,
} from "@ionic/react";
import {
  arrowDownCircleOutline,
  arrowUpCircleOutline,
  closeCircleOutline,
  callOutline,
  personCircleOutline,
  time,
  search,
} from "ionicons/icons";

import CallHistory, { CallHistoryEntry } from "../plugins/call-history";
import "./CallHistory.css"; // reuse same css

export const Recents: React.FC = () => {
  const [calls, setCalls] = useState<CallHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searchText, setSearchText] = useState("");

  useEffect(() => {
    loadCallHistory();
  }, []);

  const loadCallHistory = async () => {
    setLoading(true);
    setError("");

    try {
      if (typeof CallHistory.getCallHistory !== "function") {
        throw new Error("Call history feature is not available on this device");
      }

      const result = await CallHistory.getCallHistory({ limit: 50 });
      if (result.calls && result.calls.length > 0) {
        setCalls(result.calls);
      } else {
        setCalls(generateDummyData());
      }
    } catch (err: any) {
      console.error("Error loading call history:", err);
      setError(err.message || "Failed to load call history");
      setCalls(generateDummyData());
    } finally {
      setLoading(false);
    }
  };

  const generateDummyData = (): CallHistoryEntry[] => {
    const now = Date.now();
    return [
      {
        id: "1",
        name: "Rahul Sharma",
        number: "+91 9876543210",
        type: "INCOMING",
        date: now - 1000 * 60 * 5,
        duration: 127,
      },
      {
        id: "2",
        name: "Ananya Gupta",
        number: "+91 9876500000",
        type: "OUTGOING",
        date: now - 1000 * 60 * 30,
        duration: 45,
      },
      {
        id: "3",
        name: "Unknown Number",
        number: "Private Number",
        type: "MISSED",
        date: now - 1000 * 60 * 60 * 2,
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
      return `${diffDays} day${diffDays > 1 ? "s" : ""} ago`;
    } else {
      return date.toLocaleDateString();
    }
  };

  const formatDuration = (seconds: number) => {
    if (seconds === 0) return "";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  const getIcon = (type: string) => {
    switch (type) {
      case "INCOMING":
        return arrowDownCircleOutline;
      case "OUTGOING":
        return arrowUpCircleOutline;
      case "MISSED":
        return closeCircleOutline;
      default:
        return callOutline;
    }
  };

  const getColor = (type: string) => {
    switch (type) {
      case "INCOMING":
        return "success";
      case "OUTGOING":
        return "primary";
      case "MISSED":
        return "danger";
      default:
        return "medium";
    }
  };

  const filteredCalls = calls.filter(
    (c) =>
      c.name?.toLowerCase().includes(searchText.toLowerCase()) ||
      c.number?.toLowerCase().includes(searchText.toLowerCase())
  );

  return (
    <IonPage>
      <IonContent fullscreen>
        <IonLoading isOpen={loading} message="Loading call history..." />

        <IonListHeader lines="inset">
          Recent Calls
          <IonButton slot="end" onClick={loadCallHistory}>
            Refresh
          </IonButton>
        </IonListHeader>

        <IonSearchbar
          value={searchText}
          onIonInput={(e) => setSearchText(e.detail.value!)}
          placeholder="Search calls"
        />

        {error && (
          <div className="error-container">
            <IonText color="danger">
              <p>{error}</p>
            </IonText>
            <IonButton onClick={loadCallHistory}>Try Again</IonButton>
          </div>
        )}

        <IonList>
          {filteredCalls.map((call, idx) => (
            <IonItem key={idx}>
              <IonAvatar slot="start">
                <IonIcon icon={personCircleOutline} size="large" />
              </IonAvatar>
              <IonLabel>
                <h2>{call.name || call.number}</h2>
                <p>
                  <IonIcon
                    icon={getIcon(call.type)}
                    color={getColor(call.type)}
                  />{" "}
                  {call.type}{" "}
                  {call.duration > 0 && (
                    <>
                      <IonIcon icon={time} color="medium" />{" "}
                      {formatDuration(call.duration)}
                    </>
                  )}
                </p>
              </IonLabel>
              <IonNote slot="end">{formatDate(call.date)}</IonNote>
            </IonItem>
          ))}
        </IonList>

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
