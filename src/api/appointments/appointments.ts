import api from "../api";

export const getAppointmentsByDate = async (date: string) => {
  const response = await api.get(`/appointments/date/${date}`);

  return response.data;
};
