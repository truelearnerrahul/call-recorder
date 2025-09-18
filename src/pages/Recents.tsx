import { IonContent, IonListHeader, IonList, IonItem, IonAvatar, IonIcon, IonLabel, IonNote, IonPage } from "@ionic/react";
import { arrowDownCircleOutline, arrowUpCircleOutline, closeCircleOutline, callOutline, personCircleOutline } from "ionicons/icons";

// ✅ Recents Page
export const Recents: React.FC = () => {
    const getIcon = (type: string) => {
        switch (type) {
            case 'incoming':
                return arrowDownCircleOutline;
            case 'outgoing':
                return arrowUpCircleOutline;
            case 'missed':
                return closeCircleOutline;
            default:
                return callOutline;
        }
    };

    const getColor = (type: string) => {
        switch (type) {
            case 'incoming':
                return 'success';
            case 'outgoing':
                return 'primary';
            case 'missed':
                return 'danger';
            default:
                return 'medium';
        }
    };
    // Dummy data
    const recentCalls = [
        { id: 1, name: 'Rahul Sharma', type: 'incoming', time: '10:15 AM' },
        { id: 2, name: 'Ananya Gupta', type: 'outgoing', time: 'Yesterday' },
        { id: 3, name: 'Unknown Number', type: 'missed', time: '2 days ago' },
    ];

    return (
        <IonPage>
            <IonContent fullscreen>
                <IonListHeader lines="inset">Recent Calls</IonListHeader>
                <IonList>
                    {recentCalls.map((call) => (
                        <IonItem key={call.id}>
                            <IonAvatar slot="start">
                                <IonIcon icon={personCircleOutline} size="large" />
                            </IonAvatar>
                            <IonLabel>
                                <h2>{call.name}</h2>
                                <p>
                                    <IonIcon
                                        icon={getIcon(call.type)}
                                        color={getColor(call.type)}
                                    />{' '}
                                    {call.type}
                                </p>
                            </IonLabel>
                            <IonNote slot="end">{call.time}</IonNote>
                        </IonItem>
                    ))}
                </IonList>
            </IonContent>
        </IonPage>

    );
};