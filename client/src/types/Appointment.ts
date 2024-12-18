export interface Appointment {
  id: string;
  employeeId: string;
  date: string;
  startTime: string;
  endTime: string;
  name: string | null;
  services: number[];
  phoneNumber?: string;
  reminderSent: boolean;
}
