import { Client } from "../../types";
import api from "../api";

export const getClients = async (form: {
  name?: string;
  phoneNumber?: string;
}) => {
  const response = await api.get("/clients/", {
    params: {
      name: form.name,
      phoneNumber: form.phoneNumber,
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
