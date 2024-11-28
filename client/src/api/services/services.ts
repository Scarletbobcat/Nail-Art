import api from "../api";
import { Service } from "../../types";

export const getAllServices = async (name?: string) => {
  if (name) {
    const response = await api.get(`/services/name/${name}`);

    return response.data;
  }
  const response = await api.get("/services/");

  return response.data;
};

export const editService = async (service: Service) => {
  const response = await api.put(`/services/edit`, service);

  return response.data;
};

export const deleteService = async (service: Service) => {
  const response = await api.delete("/services/delete", { data: service });

  return response.data;
};

export const createService = async (service: Service) => {
  const response = await api.post("/services/create", service);

  return response.data;
};
