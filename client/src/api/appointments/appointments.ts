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
  if (!appointment.id) {
    throw new Error("Appointment id is required to edit an appointment");
  }
  const response = await api.put(`/appointments/edit/${appointment.id}`, appointment);

  return response.data;
};

export const createAppointment = async (appointment: Appointment) => {
  const response = await api.post(`/appointments/create`, appointment);

  return response.data;
};

export const deleteAppointment = async (appointment: Appointment) => {
  if (!appointment.id) {
    throw new Error("Appointment id is required to delete an appointment");
  }
  const response = await api.delete(`/appointments/delete/${appointment.id}`);

  return response.data;
};
