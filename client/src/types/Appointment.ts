export interface Appointment {
  id: string;
  employeeId: string;
  startsAt: string;
  endsAt: string;
  customerName?: string | null;
  name: string | null;
  services: string[];
  phoneNumber?: string;
  reminderSent?: boolean;
  reminderSentAt?: string | null;
  archivedAt?: string | null;
  showedUp: boolean;
  clientId?: string | null;
}
