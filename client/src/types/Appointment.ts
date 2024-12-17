export interface Appointment {
  id: string;
  employeeId: string;
  date: string;
  startTime: string;
  endTime: string;
  name: string;
  services: number[];
  phoneNumber?: string;
  reminderSent: boolean;
}
