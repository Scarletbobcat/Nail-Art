import { Employee } from "../../types";
import api from "../api";

// Returns all employees as an array (for dropdowns, calendar columns, etc.)
export const getAllEmployees = async () => {
  const response = await api.get("/employees/", {
    params: { size: 1000 },
  });
  return response.data.content;
};

// Returns paginated response (for list page)
export const getEmployeesPaginated = async (name?: string, page?: number, size?: number) => {
  const response = await api.get("/employees/", {
    params: {
      name: name || undefined,
      page: page ?? 0,
      size: size ?? 20,
    },
  });
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
