import api from "../api";

export const getAppointmentsByDate = async (date: string) => {
  const response = await api.get(`/appointments/date/${date}`);

  return response.data;
};

export const getAppointmentsByPhoneNumber = async (phoneNumber: string) => {
  const response = await api.get(`/appointments/search/${phoneNumber}`);

  return response.data;
};
