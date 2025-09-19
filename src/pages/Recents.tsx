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
  IonHeader,
  IonTitle,
  IonToolbar,
  IonButtons,
} from "@ionic/react";
import {
  arrowDownCircleOutline,
  arrowUpCircleOutline,
  closeCircleOutline,
  callOutline,
  personCircleOutline,
  time,
  search,
  refreshOutline,
} from "ionicons/icons";

import CallHistory, { CallHistoryEntry } from "../plugins/call-history";

export const Recents: React.FC = () => {
  const [calls, setCalls] = useState<CallHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searchText, setSearchText] = useState("");
  const [filter, setFilter] = useState("all");

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
        setCalls([]);
      }
    } catch (err: any) {
      console.error("Error loading call history:", err);
      setError(err.message || "Failed to load call history");
      setCalls([]);
    } finally {
      setLoading(false);
    }
  };

  // const generateDummyData = (): CallHistoryEntry[] => {
  //   const now = Date.now();
  //   return [
  //     {
  //       id: "1",
  //       name: "Rahul Sharma",
  //       number: "+91 9876543210",
  //       type: "INCOMING",
  //       date: now - 1000 * 60 * 5,
  //       duration: 127,
  //     },
  //     {
  //       id: "2",
  //       name: "Ananya Gupta",
  //       number: "+91 9876500000",
  //       type: "OUTGOING",
  //       date: now - 1000 * 60 * 30,
  //       duration: 45,
  //     },
  //     {
  //       id: "3",
  //       name: "Unknown Number",
  //       number: "Private Number",
  //       type: "MISSED",
  //       date: now - 1000 * 60 * 60 * 2,
  //       duration: 0,
  //     },
  //   ];
  // };

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

  const filteredCalls = calls.filter((c) => {
    // 🔍 search filter
    const matchesSearch =
      c.name?.toLowerCase().includes(searchText.toLowerCase()) ||
      c.number?.toLowerCase().includes(searchText.toLowerCase());

    // 🎯 type filter
    const matchesType =
      filter === "all" ||
      (filter === "missed" && c.type === "MISSED") ||
      (filter === "incoming" && c.type === "INCOMING") ||
      (filter === "outgoing" && c.type === "OUTGOING");

    return matchesSearch && matchesType;
  });

  return (
    <IonPage>
      <IonHeader translucent>
        <IonToolbar>
          <IonTitle className="ion-padding-start">Call History</IonTitle>
          <IonButtons slot="end">
            <IonButton onClick={loadCallHistory}>
              <IonIcon icon={refreshOutline} className="text-primary" />
            </IonButton>
          </IonButtons>
        </IonToolbar>
        <IonToolbar
          color="light"
          style={{
            "--padding-start": "0", // remove Ionic’s default padding
            "--padding-end": "0",
            "--min-height": "56px",
          }}
        >
          <div className="flex justify-around py-2 text-sm font-medium">
            <button
              className={`px-3! py-1! rounded-full! transition-colors ${filter === "all"
                ? "bg-blue-500! text-white! shadow-sm!"
                : "text-gray-600 hover:text-blue-600"
                }`}
              onClick={() => setFilter("all")}
            >
              All
            </button>
            <button
              className={`px-3! py-1! rounded-full! transition-colors ${filter === "missed"
                ? "bg-red-500 text-white shadow-sm"
                : "text-gray-600 hover:text-red-600"
                }`}
              onClick={() => setFilter("missed")}
            >
              Missed
            </button>
            <button
              className={`px-3! py-1! rounded-full! transition-colors ${filter === "incoming"
                ? "bg-green-500 text-white shadow-sm"
                : "text-gray-600 hover:text-green-600"
                }`}
              onClick={() => setFilter("incoming")}
            >
              Incoming
            </button>
            <button
              className={`px-3! py-1! rounded-full! transition-colors ${filter === "outgoing"
                ? "bg-blue-500 text-white shadow-sm"
                : "text-gray-600 hover:text-blue-600"
                }`}
              onClick={() => setFilter("outgoing")}
            >
              Outgoing
            </button>
          </div>
        </IonToolbar>

      </IonHeader>

      <IonContent fullscreen>
        <IonLoading isOpen={loading} message="Loading call history..." />

        {/* Searchbar */}
        <div className="p-3">
          <IonSearchbar
            value={searchText}
            animated
            color="light"
            onIonInput={(e) => setSearchText(e.detail.value!)}
            placeholder="Search by name or number"
            style={{
              "--background": "#f1f5f9", // Tailwind gray-100
              "--border-radius": "12px",
              "--icon-color": "#6b7280", // gray-500
            }}
          />
        </div>

        {/* Error message */}
        {error && (
          <div className="flex flex-col items-center p-6 text-center">
            <IonText color="danger">
              <p className="mb-2">{error}</p>
            </IonText>
            <IonButton onClick={loadCallHistory}>Try Again</IonButton>
          </div>
        )}

        {/* Call list */}
        {filteredCalls.length > 0 && (
          <IonList lines="none">
            {filteredCalls.map((call, idx) => (
              <IonItem
                key={idx}
                button
                detail={false}
                className="rounded-xl my-1"
                style={{
                  "--padding-start": "1rem", // same as px-4
                  "--inner-padding-end": "1rem",
                  "--min-height": "72px",
                }}
              >
                <IonAvatar slot="start" className="mr-4">
                  <div className="bg-indigo-100 dark:bg-indigo-800 text-indigo-600 dark:text-indigo-400 w-12 h-12 flex items-center justify-center rounded-full text-lg font-bold">
                    {call.name?.[0] || call.number?.[0] || "?"}
                  </div>
                </IonAvatar>

                <IonLabel>
                  <h2 className="font-medium text-gray-900 dark:text-gray-100">
                    {call.name || call.number}
                  </h2>
                  <p className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400">
                    <IonIcon
                      icon={getIcon(call.type)}
                      color={getColor(call.type)}
                    />
                    {call.type}
                    {call.duration > 0 && (
                      <>
                        <IonIcon icon={time} color="medium" />
                        {formatDuration(call.duration)}
                      </>
                    )}
                  </p>
                </IonLabel>

                <IonNote slot="end" className="text-xs text-gray-400 dark:text-gray-600">
                  {formatDate(call.date)}
                </IonNote>
              </IonItem>
            ))}
          </IonList>
        )}

        {/* No calls */}
        {filteredCalls.length === 0 && !loading && (
          <div className="flex justify-center items-center h-full text-gray-500">
            <IonText>
              No Call History Found
            </IonText>
          </div>
        )}
      </IonContent>
    </IonPage>

  );
};
