export interface Appointment {
  id: string;
  employeeId: string;
  date: string;
  startTime: string;
  endTime: string;
  name: string;
  services: string[];
  phoneNumber?: string;
  reminderSent: boolean;
}
