import api from "../api";
import { Appointment } from "../../types/Appointment";

export const getAppointmentsByDate = async (date: string) => {
  const response = await api.get(`/appointments/date/${date}`);

  return response.data;
};

export const getAppointmentsByPhoneNumber = async (phoneNumber: string) => {
  const response = await api.get(`/appointments/search/${phoneNumber}`);

  return response.data;
};

export const editAppointment = async (appointment: Appointment) => {
  const response = await api.put(`/appointments/edit`, appointment);

  return response.data;
};

export const createAppointment = async (appointment: Appointment) => {
  const response = await api.post(`/appointments/create`, appointment);

  return response.data;
};
