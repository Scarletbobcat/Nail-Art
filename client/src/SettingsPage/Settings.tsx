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
import { SPACING, MAX_CONTENT_WIDTH, TIMEZONES } from "../constants/design";
import {
  getOrganizationSettings,
  updateOrganizationSettings,
  type OrganizationSettingsUpdate,
} from "../api/organization";
import { getHttpStatus } from "../utils/httpError";
import { formatPhoneInput } from "../utils/phone";

type FormState = {
  name: string;
  businessPhone: string;
  timezone: string;
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
  // staleTime Infinity + no window refetch, `data` only changes on those events.
  useEffect(() => {
    if (!data) return;
    setForm({
      name: data.name ?? "",
      businessPhone: data.businessPhone ?? "",
      timezone: data.timezone ?? "America/New_York",
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

  // Reminders are effectively on only when Twilio is configured AND the flag is
  // set; when unconfigured the toggle reads off and is locked (operator-managed).
  const smsToggleChecked = data.twilioConfigured && form.smsRemindersEnabled;

  const timezoneOptions = TIMEZONES.includes(form.timezone)
    ? TIMEZONES
    : [form.timezone, ...TIMEZONES];

  const handleSave = async () => {
    const payload: OrganizationSettingsUpdate = {
      name: form.name,
      businessPhone: form.businessPhone,
      timezone: form.timezone,
    };
    // Only send the toggle when the owner can actually control it (Twilio
    // configured). Otherwise leave the stored flag untouched.
    if (data.twilioConfigured) {
      payload.smsRemindersEnabled = form.smsRemindersEnabled;
    }

    setSaving(true);
    try {
      await updateOrganizationSettings(payload);
      await queryClient.invalidateQueries({ queryKey: organizationSettingsQueryKey });
      setSnack({ msg: "Settings saved.", severity: "success" });
    } catch (error) {
      setSnack({
        msg:
          getHttpStatus(error) === 400
            ? "SMS reminders can't be enabled until Twilio is configured for this salon."
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
              onChange={(e) => {
                const next = formatPhoneInput(e.target.value, form.businessPhone);
                if (next !== null) update({ businessPhone: next });
              }}
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
          <Box>
            <FormControlLabel
              control={
                <Switch
                  checked={smsToggleChecked}
                  disabled={!data.twilioConfigured}
                  onChange={(e) => update({ smsRemindersEnabled: e.target.checked })}
                />
              }
              label="Send SMS appointment reminders"
            />
            {!data.twilioConfigured && (
              <FormHelperText>
                SMS reminders aren&apos;t set up for this salon yet. Contact support to enable them.
              </FormHelperText>
            )}
          </Box>

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
