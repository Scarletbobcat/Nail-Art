import api from "../api";
import { Service } from "../../types";

// Returns all services as an array (for dropdowns, calendar, etc.)
export const getAllServices = async () => {
  const response = await api.get("/services/", {
    params: { size: 1000 },
  });
  return response.data.content;
};

// Returns paginated response (for list page)
export const getServicesPaginated = async (name?: string, page?: number, size?: number) => {
  const response = await api.get("/services/", {
    params: {
      name: name || undefined,
      page: page ?? 0,
      size: size ?? 20,
    },
  });
  return response.data;
};

export const editService = async (service: Service) => {
  const response = await api.put(`/services/edit/${service.id}`, {
    name: service.name,
  });
  return response.data;
};

export const deleteService = async (service: Service) => {
  const response = await api.delete(`/services/delete/${service.id}`);
  return response.data;
};

export const createService = async (service: Service) => {
  const response = await api.post("/services/create", {
    name: service.name,
  });
  return response.data;
};
