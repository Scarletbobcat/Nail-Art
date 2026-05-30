import api from "../api";

export type MeResponse = {
  user: {
    id: string;
    username: string;
    // null for a platform admin (org-less); a role string for owners/staff.
    role: string | null;
    isPlatformAdmin: boolean;
  };
  // null for a platform admin (no organization); always present for owners/staff.
  organization: {
    id: string;
    name: string;
    timezone: string;
    businessPhone: string;
  } | null;
};

export const fetchMe = async (): Promise<MeResponse> => {
  const response = await api.get<MeResponse>("/users/me");
  return response.data;
};
