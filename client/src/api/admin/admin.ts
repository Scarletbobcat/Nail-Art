import api from "../api";

// One salon row in the operator console list.
export type AdminSalonSummary = {
  id: string;
  name: string;
  timezone: string;
  businessPhone: string | null;
  smsRemindersEnabled: boolean;
  twilioConfigured: boolean;
};

// A salon's profile + SMS view (same shape the owner Settings endpoint returns).
export type AdminSalonSettings = {
  name: string;
  businessPhone: string | null;
  timezone: string;
  smsRemindersEnabled: boolean;
  twilioConfigured: boolean;
};

// Every field optional: omit one to leave the stored value untouched.
export type AdminSalonUpdate = {
  name?: string;
  businessPhone?: string;
  timezone?: string;
  smsRemindersEnabled?: boolean;
};

// Twilio config read. The auth token is write-only and never returned.
export type AdminTwilioConfig = {
  configured: boolean;
  accountSid: string | null;
  phoneNumber: string | null;
};

// SID/phone set when non-blank; authToken set when non-blank, else left untouched.
export type AdminTwilioUpdate = {
  accountSid?: string;
  phoneNumber?: string;
  authToken?: string;
};

export type CreateSalonRequest = {
  name: string;
  timezone?: string;
  businessPhone?: string;
  ownerUsername: string;
  ownerPassword: string;
};

export type CreateSalonResponse = {
  organizationId: string;
  name: string;
  ownerUserId: string;
  ownerUsername: string;
};

export const listSalons = async (): Promise<AdminSalonSummary[]> => {
  const response = await api.get<AdminSalonSummary[]>("/admin/organizations");
  return response.data;
};

export const getSalon = async (organizationId: string): Promise<AdminSalonSettings> => {
  const response = await api.get<AdminSalonSettings>(`/admin/organizations/${organizationId}`);
  return response.data;
};

export const updateSalon = async (
  organizationId: string,
  update: AdminSalonUpdate
): Promise<AdminSalonSettings> => {
  const response = await api.put<AdminSalonSettings>(`/admin/organizations/${organizationId}`, update);
  return response.data;
};

export const getSalonTwilio = async (organizationId: string): Promise<AdminTwilioConfig> => {
  const response = await api.get<AdminTwilioConfig>(`/admin/organizations/${organizationId}/twilio`);
  return response.data;
};

export const updateSalonTwilio = async (
  organizationId: string,
  update: AdminTwilioUpdate
): Promise<AdminTwilioConfig> => {
  const response = await api.put<AdminTwilioConfig>(`/admin/organizations/${organizationId}/twilio`, update);
  return response.data;
};

export const createSalon = async (request: CreateSalonRequest): Promise<CreateSalonResponse> => {
  const response = await api.post<CreateSalonResponse>("/admin/organizations", request);
  return response.data;
};

export type AdminSalonUser = {
  id: string;
  username: string;
  role: string;
};

// Both optional: omit/blank to leave unchanged. Password is write-only.
export type AdminUserUpdate = {
  username?: string;
  password?: string;
};

export const listSalonUsers = async (organizationId: string): Promise<AdminSalonUser[]> => {
  const response = await api.get<AdminSalonUser[]>(`/admin/organizations/${organizationId}/users`);
  return response.data;
};

export const updateSalonUser = async (
  organizationId: string,
  userId: string,
  update: AdminUserUpdate
): Promise<AdminSalonUser> => {
  const response = await api.put<AdminSalonUser>(
    `/admin/organizations/${organizationId}/users/${userId}`,
    update
  );
  return response.data;
};
