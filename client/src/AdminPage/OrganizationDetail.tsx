import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";

import AnimatedPage from "../components/AnimatedPage";
import PageHeader from "../components/PageHeader";
import PageSkeleton from "../components/PageSkeleton";
import { SPACING, MAX_CONTENT_WIDTH, TIMEZONES } from "../constants/design";
import { getHttpStatus } from "../utils/httpError";
import { formatPhoneInput } from "../utils/phone";
import {
  getSalon,
  getSalonTwilio,
  updateSalon,
  updateSalonTwilio,
} from "../api/admin";
import { adminSalonsQueryKey } from "./Organizations";

type Snack = { msg: string; severity: "success" | "error" };

export default function OrganizationDetail() {
  const { organizationId = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const salonQuery = useQuery({
    queryKey: ["admin-salon", organizationId],
    queryFn: () => getSalon(organizationId),
    refetchOnWindowFocus: false,
    enabled: Boolean(organizationId),
  });
  const twilioQuery = useQuery({
    queryKey: ["admin-salon-twilio", organizationId],
    queryFn: () => getSalonTwilio(organizationId),
    refetchOnWindowFocus: false,
    enabled: Boolean(organizationId),
  });

  const [profile, setProfile] = useState({
    name: "",
    businessPhone: "",
    timezone: "America/New_York",
    smsRemindersEnabled: false,
  });
  const [twilio, setTwilio] = useState({ accountSid: "", phoneNumber: "", authToken: "" });
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingTwilio, setSavingTwilio] = useState(false);
  const [snack, setSnack] = useState<Snack | null>(null);

  useEffect(() => {
    if (!salonQuery.data) return;
    setProfile({
      name: salonQuery.data.name ?? "",
      businessPhone: salonQuery.data.businessPhone ?? "",
      timezone: salonQuery.data.timezone ?? "America/New_York",
      smsRemindersEnabled: salonQuery.data.smsRemindersEnabled,
    });
  }, [salonQuery.data]);

  useEffect(() => {
    if (!twilioQuery.data) return;
    setTwilio({
      accountSid: twilioQuery.data.accountSid ?? "",
      phoneNumber: twilioQuery.data.phoneNumber ?? "",
      authToken: "",
    });
  }, [twilioQuery.data]);

  if (salonQuery.isLoading || twilioQuery.isLoading) {
    return <PageSkeleton />;
  }

  if (salonQuery.isError || !salonQuery.data || !twilioQuery.data) {
    return (
      <AnimatedPage>
        <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
          <Alert severity="error">Could not load this salon. Check your connection and refresh.</Alert>
        </Box>
      </AnimatedPage>
    );
  }

  const twilioConfigured = salonQuery.data.twilioConfigured;

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin-salon", organizationId] }),
      queryClient.invalidateQueries({ queryKey: ["admin-salon-twilio", organizationId] }),
      queryClient.invalidateQueries({ queryKey: adminSalonsQueryKey }),
    ]);
  };

  const handleSaveProfile = async () => {
    setSavingProfile(true);
    try {
      await updateSalon(organizationId, {
        name: profile.name,
        businessPhone: profile.businessPhone,
        timezone: profile.timezone,
        smsRemindersEnabled: profile.smsRemindersEnabled,
      });
      await invalidate();
      setSnack({ msg: "Profile saved.", severity: "success" });
    } catch (error) {
      setSnack({
        msg:
          getHttpStatus(error) === 400
            ? "SMS reminders can't be enabled until Twilio is fully configured."
            : "Could not save the profile. Please try again.",
        severity: "error",
      });
    } finally {
      setSavingProfile(false);
    }
  };

  const handleSaveTwilio = async () => {
    setSavingTwilio(true);
    try {
      await updateSalonTwilio(organizationId, {
        accountSid: twilio.accountSid,
        phoneNumber: twilio.phoneNumber,
        // Blank token is intentionally sent as empty and treated as "leave unchanged".
        authToken: twilio.authToken,
      });
      setTwilio((current) => ({ ...current, authToken: "" }));
      await invalidate();
      setSnack({ msg: "Twilio configuration saved.", severity: "success" });
    } catch {
      setSnack({ msg: "Could not save Twilio configuration. Please try again.", severity: "error" });
    } finally {
      setSavingTwilio(false);
    }
  };

  const timezoneOptions = TIMEZONES.includes(profile.timezone)
    ? TIMEZONES
    : [profile.timezone, ...TIMEZONES];

  return (
    <AnimatedPage>
      <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/Admin")} sx={{ mb: 1 }}>
          All salons
        </Button>

        <Paper variant="outlined" sx={{ p: SPACING.section, mb: 2 }}>
          <PageHeader title={salonQuery.data.name} subtitle="Salon profile and SMS reminders" />

          <Stack spacing={2} sx={{ mb: 1 }}>
            <TextField
              label="Salon name"
              fullWidth
              size="small"
              value={profile.name}
              onChange={(e) => setProfile((p) => ({ ...p, name: e.target.value }))}
            />
            <TextField
              label="Business phone"
              fullWidth
              size="small"
              value={profile.businessPhone}
              onChange={(e) => {
                const next = formatPhoneInput(e.target.value, profile.businessPhone);
                if (next !== null) setProfile((p) => ({ ...p, businessPhone: next }));
              }}
            />
            <TextField
              select
              label="Timezone"
              fullWidth
              size="small"
              value={profile.timezone}
              onChange={(e) => setProfile((p) => ({ ...p, timezone: e.target.value }))}
            >
              {timezoneOptions.map((tz) => (
                <MenuItem key={tz} value={tz}>
                  {tz}
                </MenuItem>
              ))}
            </TextField>
          </Stack>

          <Box sx={{ mt: 1 }}>
            <FormControlLabel
              control={
                <Switch
                  checked={twilioConfigured && profile.smsRemindersEnabled}
                  disabled={!twilioConfigured}
                  onChange={(e) => setProfile((p) => ({ ...p, smsRemindersEnabled: e.target.checked }))}
                />
              }
              label="Send SMS appointment reminders"
            />
            {!twilioConfigured && (
              <FormHelperText>Configure Twilio below before enabling SMS reminders.</FormHelperText>
            )}
          </Box>

          <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
            <Button
              variant="contained"
              onClick={handleSaveProfile}
              disabled={savingProfile}
              startIcon={savingProfile ? <CircularProgress size={16} color="inherit" /> : undefined}
              sx={{ minWidth: 120 }}
            >
              {savingProfile ? "Saving…" : "Save profile"}
            </Button>
          </Box>
        </Paper>

        <Paper variant="outlined" sx={{ p: SPACING.section }}>
          <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 0.5 }}>
            <Typography variant="subtitle1" fontWeight={700}>
              Twilio configuration
            </Typography>
            <Chip
              size="small"
              label={twilioConfigured ? "Configured" : "Not configured"}
              color={twilioConfigured ? "success" : "default"}
              variant={twilioConfigured ? "filled" : "outlined"}
            />
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            This salon sends reminders from its own Twilio account. The auth token is write-only — it
            is never shown; leave it blank to keep the current one.
          </Typography>

          <Stack spacing={2}>
            <TextField
              label="Account SID"
              fullWidth
              size="small"
              value={twilio.accountSid}
              onChange={(e) => setTwilio((t) => ({ ...t, accountSid: e.target.value }))}
            />
            <TextField
              label="Sending phone number"
              fullWidth
              size="small"
              value={twilio.phoneNumber}
              onChange={(e) => setTwilio((t) => ({ ...t, phoneNumber: e.target.value }))}
            />
            <TextField
              label="Auth token"
              type="password"
              fullWidth
              size="small"
              value={twilio.authToken}
              placeholder={twilioConfigured ? "•••••••• (leave blank to keep current)" : "Enter the Twilio auth token"}
              onChange={(e) => setTwilio((t) => ({ ...t, authToken: e.target.value }))}
            />
          </Stack>

          <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
            <Button
              variant="contained"
              onClick={handleSaveTwilio}
              disabled={savingTwilio}
              startIcon={savingTwilio ? <CircularProgress size={16} color="inherit" /> : undefined}
              sx={{ minWidth: 120 }}
            >
              {savingTwilio ? "Saving…" : "Save Twilio"}
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
