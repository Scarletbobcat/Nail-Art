import api from "../api";

export type OrganizationSettings = {
  name: string;
  businessPhone: string | null;
  timezone: string;
  smsRemindersEnabled: boolean;
  // Twilio credentials are operator-managed; the owner view only learns whether
  // they are configured (so the SMS toggle knows if it can be enabled).
  twilioConfigured: boolean;
};

// Owners update profile + the SMS toggle only. Every field optional: omit one to
// leave the stored value untouched.
export type OrganizationSettingsUpdate = {
  name?: string;
  businessPhone?: string;
  timezone?: string;
  smsRemindersEnabled?: boolean;
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
