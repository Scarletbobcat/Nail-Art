import api from "../api";

export type OrganizationSettings = {
  name: string;
  businessPhone: string | null;
  timezone: string;
  smsRemindersEnabled: boolean;
  twilioConfigured: boolean;
  twilioAccountSid: string | null;
  twilioPhoneNumberMasked: string | null;
};

// Every field optional: omit one to leave the stored value untouched. The auth
// token is write-only — only send it to set or replace it, never to read it.
export type OrganizationSettingsUpdate = {
  name?: string;
  businessPhone?: string;
  timezone?: string;
  smsRemindersEnabled?: boolean;
  twilioAccountSid?: string;
  twilioAuthToken?: string;
  twilioPhoneNumber?: string;
};

export const getOrganizationSettings = async (): Promise<OrganizationSettings> => {
  const response = await api.get<OrganizationSettings>("/organization");
  return response.data;
};

export const updateOrganizationSettings = async (
  update: OrganizationSettingsUpdate
): Promise<OrganizationSettings> => {
  const response = await api.put<OrganizationSettings>("/organization", update);
  return response.data;
};
