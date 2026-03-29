import { Client } from "../../types";
import api from "../api";

// Returns all clients as an array (for dropdowns, calendar, etc.)
export const getClients = async (form: {
  name?: string;
  phoneNumber?: string;
}) => {
  const response = await api.get("/clients/", {
    params: {
      name: form.name,
      phoneNumber: form.phoneNumber,
      size: 1000,
    },
  });
  return response.data.content;
};

// Returns paginated response (for list page)
export const getClientsPaginated = async (form: {
  name?: string;
  phoneNumber?: string;
  page?: number;
  size?: number;
}) => {
  const response = await api.get("/clients/", {
    params: {
      name: form.name,
      phoneNumber: form.phoneNumber,
      page: form.page ?? 0,
      size: form.size ?? 20,
    },
  });
  return response.data;
};

export const createClient = async (form: Client) => {
  const response = await api.post("/clients/create", form);
  return response.data;
};

export const editClient = async (form: Client) => {
  const response = await api.put(`/clients/edit`, form);
  return response.data;
};

export const deleteClient = async (form: Client) => {
  const response = await api.delete(`/clients/delete`, {
    data: form,
  });
  return response.data;
};
