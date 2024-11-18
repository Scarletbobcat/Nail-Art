import api from "../api";

export const getAllEmployees = async () => {
  const response = await api.get("/employees/");

  return response.data;
};
