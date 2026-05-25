import api from "../api";

export type MeResponse = {
  user: {
    id: string;
    username: string;
    role: string;
  };
  organization: {
    id: string;
    name: string;
    timezone: string;
    businessPhone: string;
  };
};

export const fetchMe = async (): Promise<MeResponse> => {
  const response = await api.get<MeResponse>("/users/me");
  return response.data;
};
