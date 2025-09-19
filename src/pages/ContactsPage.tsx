import React, { useEffect, useState } from "react";
import {
  IonPage,
  IonContent,
  IonList,
  IonItem,
  IonLabel,
  IonAvatar,
  IonIcon,
  IonInfiniteScroll,
  IonInfiniteScrollContent,
  IonLoading,
} from "@ionic/react";
import { personCircleOutline } from "ionicons/icons";
import Contacts, { Contact } from "../plugins/contacts";

export const ContactsPage: React.FC = () => {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(false);
  const [offset, setOffset] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const LIMIT = 50;

  const loadContacts = async (append = false) => {
    try {
      setLoading(true);
      const result = await Contacts.getContacts({ offset, limit: LIMIT });
      console.log("📱 Contacts fetched:", result.contacts.length);

      if (result.contacts.length < LIMIT) {
        setHasMore(false); // no more contacts
      }

      setContacts((prev) =>
        append ? [...prev, ...result.contacts] : result.contacts
      );

      setOffset((prev) => prev + LIMIT);
    } catch (err) {
      console.error("Error fetching contacts", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadContacts();
  }, []);

  return (
    <IonPage>
      <IonContent>
        <IonLoading isOpen={loading && contacts.length === 0} message="Loading contacts..." />
        <IonList>
          {contacts.map((c) => (
            <IonItem key={c.id}>
              <IonAvatar slot="start">
                <IonIcon icon={personCircleOutline} size="large" />
              </IonAvatar>
              <IonLabel>
                <h2>{c.name}</h2>
                <p>{c.phoneNumbers.join(", ")}</p>
              </IonLabel>
            </IonItem>
          ))}
        </IonList>

        <IonInfiniteScroll
          threshold="100px"
          disabled={!hasMore}
          onIonInfinite={(ev) => {
            loadContacts(true).then(() => (ev.target as HTMLIonInfiniteScrollElement).complete());
          }}
        >
          <IonInfiniteScrollContent loadingText="Loading more contacts..." />
        </IonInfiniteScroll>
      </IonContent>
    </IonPage>
  );
};
