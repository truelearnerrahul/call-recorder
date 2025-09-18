import { IonContent, IonListHeader, IonGrid, IonRow, IonCol, IonCard, IonCardContent, IonIcon, IonPage } from "@ionic/react";
import { personCircleOutline } from "ionicons/icons";

const contacts = [
  { id: 1, name: 'Rahul Sharma' },
  { id: 2, name: 'Ananya Gupta' },
  { id: 3, name: 'Amit Patel' },
  { id: 4, name: 'Sneha Reddy' },
  { id: 5, name: 'Arjun Verma' },
  { id: 6, name: 'Meera Iyer' },
];

export const Contacts: React.FC = () => {
  return (
    <IonPage>
      <IonContent fullscreen>
        <IonListHeader lines="inset">Contacts</IonListHeader>
        <IonGrid>
          <IonRow>
            {contacts.map((contact) => (
              <IonCol size="6" key={contact.id}>
                <IonCard className="ion-text-center">
                  <IonCardContent>
                    <IonIcon
                      icon={personCircleOutline}
                      size="large"
                      color="primary"
                    />
                    <h3 className="mt-2">{contact.name}</h3>
                  </IonCardContent>
                </IonCard>
              </IonCol>
            ))}
          </IonRow>
        </IonGrid>
      </IonContent>
    </IonPage>
  );
};