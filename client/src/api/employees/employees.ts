import { Employee } from "../../types";
import api from "../api";

export const getAllEmployees = async (name?: string) => {
  if (name) {
    const response = await api.get(`/employees/name/${name}`);

    return response.data;
  }
  const response = await api.get("/employees/");

  return response.data;
};

export const editEmployee = async (employee: Employee) => {
  const response = await api.put(`/employees/edit`, employee);

  return response.data;
};

export const deleteEmployee = async (employee: Employee) => {
  const response = await api.delete("/employees/delete", { data: employee });

  return response.data;
};

export const createEmployee = async (employee: Employee) => {
  const response = await api.post("/employees/create", employee);

  return response.data;
};

export const getEmployeeByName = async (name: string) => {
  const response = await api.get(`/employees/name/${name}`);

  return response.data;
};
