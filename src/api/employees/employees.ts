import { Employee } from "../../types";
import api from "../api";

export const getAllEmployees = async () => {
  const response = await api.get("/employees/");

  return response.data;
};

export const editEmployee = async (employee: Employee) => {
  const response = await api.put(`/employees/edit`, employee);

  return response.data;
};
