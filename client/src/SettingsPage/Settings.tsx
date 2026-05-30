import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  FormControlLabel,
  FormHelperText,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";

import AnimatedPage from "../components/AnimatedPage";
import PageHeader from "../components/PageHeader";
import PageSkeleton from "../components/PageSkeleton";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";
import {
  getOrganizationSettings,
  updateOrganizationSettings,
  type OrganizationSettingsUpdate,
} from "../api/organization";
import { getHttpStatus } from "../utils/httpError";

const TIMEZONES = [
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Phoenix",
  "America/Los_Angeles",
  "America/Anchorage",
  "Pacific/Honolulu",
];

type FormState = {
  name: string;
  businessPhone: string;
  timezone: string;
  twilioAccountSid: string;
  twilioAuthToken: string;
  twilioPhoneNumber: string;
  smsRemindersEnabled: boolean;
};

type Snack = { msg: string; severity: "success" | "error" };

export const organizationSettingsQueryKey = ["organization-settings"] as const;

export default function Settings() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useQuery({
    queryKey: organizationSettingsQueryKey,
    queryFn: getOrganizationSettings,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  const [form, setForm] = useState<FormState | null>(null);
  const [saving, setSaving] = useState(false);
  const [snack, setSnack] = useState<Snack | null>(null);

  // Reset the form from server state on first load and after each save. With
  // staleTime Infinity + no window refetch, `data` only changes on those events,
  // so in-progress edits are never clobbered by a background refetch.
  useEffect(() => {
    if (!data) return;
    setForm({
      name: data.name ?? "",
      businessPhone: data.businessPhone ?? "",
      timezone: data.timezone ?? "America/New_York",
      twilioAccountSid: data.twilioAccountSid ?? "",
      twilioAuthToken: "",
      twilioPhoneNumber: "",
      smsRemindersEnabled: data.smsRemindersEnabled,
    });
  }, [data]);

  if (isLoading) {
    return <PageSkeleton />;
  }

  if (isError || !data) {
    return (
      <AnimatedPage>
        <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
          <Alert severity="error">
            Could not load settings. Check your connection and refresh.
          </Alert>
        </Box>
      </AnimatedPage>
    );
  }

  if (!form) {
    return <PageSkeleton />;
  }

  const update = (patch: Partial<FormState>) =>
    setForm((current) => (current ? { ...current, ...patch } : current));

  const tokenBlank = form.twilioAuthToken.trim() === "";
  const allTwilioFieldsFilled =
    form.twilioAccountSid.trim() !== "" &&
    form.twilioAuthToken.trim() !== "" &&
    form.twilioPhoneNumber.trim() !== "";
  // Mirrors the server gate: enable allowed when the server already holds creds
  // (configured + token field left blank) OR all three fields are freshly filled.
  const canEnableSms = (data.twilioConfigured && tokenBlank) || allTwilioFieldsFilled;
  const smsSwitchDisabled = !canEnableSms && !form.smsRemindersEnabled;

  const timezoneOptions = TIMEZONES.includes(form.timezone)
    ? TIMEZONES
    : [form.timezone, ...TIMEZONES];

  const handleSave = async () => {
    const payload: OrganizationSettingsUpdate = {
      name: form.name,
      businessPhone: form.businessPhone,
      timezone: form.timezone,
      twilioAccountSid: form.twilioAccountSid,
      smsRemindersEnabled: form.smsRemindersEnabled,
    };
    // Write-only / leave-untouched: only send the token and number when entered.
    if (form.twilioAuthToken.trim() !== "") {
      payload.twilioAuthToken = form.twilioAuthToken;
    }
    if (form.twilioPhoneNumber.trim() !== "") {
      payload.twilioPhoneNumber = form.twilioPhoneNumber;
    }

    setSaving(true);
    try {
      await updateOrganizationSettings(payload);
      // Spinner stays until the follow-up GET resolves, so the form reflects the
      // committed state (incl. server-side auto-disable) before we stop saving.
      await queryClient.invalidateQueries({ queryKey: organizationSettingsQueryKey });
      setSnack({ msg: "Settings saved.", severity: "success" });
    } catch (error) {
      setSnack({
        msg:
          getHttpStatus(error) === 400
            ? "SMS reminders require Account SID, Auth Token, and Phone Number to be set."
            : "Settings could not be saved. Please try again.",
        severity: "error",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <AnimatedPage>
      <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
        <Paper variant="outlined" sx={{ p: SPACING.section }}>
          <PageHeader title="Settings" subtitle="Manage your salon profile and SMS reminders" />

          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
            Salon profile
          </Typography>
          <Stack spacing={2} sx={{ mb: 1 }}>
            <TextField
              label="Salon name"
              fullWidth
              size="small"
              value={form.name}
              onChange={(e) => update({ name: e.target.value })}
            />
            <TextField
              label="Business phone"
              fullWidth
              size="small"
              value={form.businessPhone}
              onChange={(e) => update({ businessPhone: e.target.value })}
            />
            <TextField
              select
              label="Timezone"
              fullWidth
              size="small"
              value={form.timezone}
              onChange={(e) => update({ timezone: e.target.value })}
            >
              {timezoneOptions.map((tz) => (
                <MenuItem key={tz} value={tz}>
                  {tz}
                </MenuItem>
              ))}
            </TextField>
          </Stack>

          <Divider sx={{ my: 3 }} />

          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
            SMS reminders
          </Typography>
          <Stack spacing={2}>
            <TextField
              label="Twilio Account SID"
              fullWidth
              size="small"
              value={form.twilioAccountSid}
              onChange={(e) => update({ twilioAccountSid: e.target.value })}
            />
            <TextField
              label="Twilio Auth Token"
              type="password"
              fullWidth
              size="small"
              value={form.twilioAuthToken}
              onChange={(e) => update({ twilioAuthToken: e.target.value })}
              placeholder={data.twilioConfigured ? "•••••••• (token saved)" : "Paste auth token"}
              helperText={
                data.twilioConfigured
                  ? "Token is saved. Enter a new value only to replace it."
                  : "Required to enable SMS reminders."
              }
            />
            <TextField
              label="Twilio Phone Number"
              fullWidth
              size="small"
              value={form.twilioPhoneNumber}
              onChange={(e) => update({ twilioPhoneNumber: e.target.value })}
              placeholder={data.twilioPhoneNumberMasked ?? "+15555550100"}
              helperText={
                data.twilioPhoneNumberMasked
                  ? `Saved: ${data.twilioPhoneNumberMasked}. Enter a new value only to replace it.`
                  : "The number reminders are sent from."
              }
            />
            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.smsRemindersEnabled}
                    disabled={smsSwitchDisabled}
                    onChange={(e) => update({ smsRemindersEnabled: e.target.checked })}
                  />
                }
                label="Send SMS appointment reminders"
              />
              {smsSwitchDisabled && <FormHelperText>Add Twilio config first</FormHelperText>}
            </Box>
          </Stack>

          <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={saving}
              startIcon={saving ? <CircularProgress size={16} color="inherit" /> : undefined}
              sx={{ minWidth: 120 }}
            >
              {saving ? "Saving…" : "Save"}
            </Button>
          </Box>
        </Paper>
      </Box>
      <Snackbar
        open={Boolean(snack)}
        autoHideDuration={5000}
        onClose={() => setSnack(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        {snack ? (
          <Alert severity={snack.severity} variant="filled" onClose={() => setSnack(null)}>
            {snack.msg}
          </Alert>
        ) : undefined}
      </Snackbar>
    </AnimatedPage>
  );
}
