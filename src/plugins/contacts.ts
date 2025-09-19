import { registerPlugin } from "@capacitor/core";

export interface Contact {
  id: string;
  name: string;
  phoneNumbers: string[];
}

export interface ContactsPlugin {
  getContacts(options: { offset?: number; limit?: number }): Promise<{ contacts: Contact[] }>;
}

const Contacts = registerPlugin<ContactsPlugin>("Contacts");

export default Contacts;
