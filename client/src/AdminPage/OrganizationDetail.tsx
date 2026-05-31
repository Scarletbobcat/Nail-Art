import { useState, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditIcon from "@mui/icons-material/Edit";

import AnimatedPage from "../components/AnimatedPage";
import PageHeader from "../components/PageHeader";
import PageSkeleton from "../components/PageSkeleton";
import { SPACING, MAX_CONTENT_WIDTH, TIMEZONES } from "../constants/design";
import { getHttpStatus } from "../utils/httpError";
import { formatPhoneInput } from "../utils/phone";
import {
  getSalon,
  getSalonTwilio,
  listSalonUsers,
  updateSalon,
  updateSalonTwilio,
  updateSalonUser,
} from "../api/admin";
import { adminSalonsQueryKey } from "./Organizations";

type Snack = { msg: string; severity: "success" | "error" };

function ReadRow({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, py: 0.75 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500, textAlign: "right", wordBreak: "break-word" }}>
        {value}
      </Typography>
    </Box>
  );
}

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
  const usersQuery = useQuery({
    queryKey: ["admin-salon-users", organizationId],
    queryFn: () => listSalonUsers(organizationId),
    refetchOnWindowFocus: false,
    enabled: Boolean(organizationId),
  });

  // Read-first: each section is a display until its Edit button opens a form.
  const [editingProfile, setEditingProfile] = useState(false);
  const [editingTwilio, setEditingTwilio] = useState(false);
  const [editingUserId, setEditingUserId] = useState<string | null>(null);

  const [profileForm, setProfileForm] = useState({
    name: "",
    businessPhone: "",
    timezone: "America/New_York",
    smsRemindersEnabled: false,
  });
  const [twilioForm, setTwilioForm] = useState({ accountSid: "", phoneNumber: "", authToken: "" });
  const [userForm, setUserForm] = useState({ username: "", password: "" });
  const [saving, setSaving] = useState(false);
  const [snack, setSnack] = useState<Snack | null>(null);

  if (salonQuery.isLoading || twilioQuery.isLoading || usersQuery.isLoading) {
    return <PageSkeleton />;
  }

  if (
    salonQuery.isError ||
    twilioQuery.isError ||
    usersQuery.isError ||
    !salonQuery.data ||
    !twilioQuery.data ||
    !usersQuery.data
  ) {
    return (
      <AnimatedPage>
        <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
          <Alert severity="error">Could not load this salon. Check your connection and refresh.</Alert>
        </Box>
      </AnimatedPage>
    );
  }

  const salon = salonQuery.data;
  const twilio = twilioQuery.data;
  const users = usersQuery.data;
  const twilioConfigured = salon.twilioConfigured;

  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin-salon", organizationId] }),
      queryClient.invalidateQueries({ queryKey: ["admin-salon-twilio", organizationId] }),
      queryClient.invalidateQueries({ queryKey: ["admin-salon-users", organizationId] }),
      queryClient.invalidateQueries({ queryKey: adminSalonsQueryKey }),
    ]);

  const startEditProfile = () => {
    setProfileForm({
      name: salon.name ?? "",
      businessPhone: salon.businessPhone ?? "",
      timezone: salon.timezone ?? "America/New_York",
      smsRemindersEnabled: salon.smsRemindersEnabled,
    });
    setEditingProfile(true);
  };

  const startEditTwilio = () => {
    setTwilioForm({
      accountSid: twilio.accountSid ?? "",
      phoneNumber: twilio.phoneNumber ?? "",
      authToken: "",
    });
    setEditingTwilio(true);
  };

  const startEditUser = (userId: string, username: string) => {
    setUserForm({ username, password: "" });
    setEditingUserId(userId);
  };

  const handleSaveProfile = async () => {
    setSaving(true);
    try {
      await updateSalon(organizationId, {
        name: profileForm.name,
        businessPhone: profileForm.businessPhone,
        timezone: profileForm.timezone,
        ...(twilioConfigured ? { smsRemindersEnabled: profileForm.smsRemindersEnabled } : {}),
      });
      await invalidate();
      setEditingProfile(false);
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
      setSaving(false);
    }
  };

  const handleSaveTwilio = async () => {
    setSaving(true);
    try {
      await updateSalonTwilio(organizationId, {
        accountSid: twilioForm.accountSid,
        phoneNumber: twilioForm.phoneNumber,
        authToken: twilioForm.authToken, // blank = leave unchanged
      });
      await invalidate();
      setEditingTwilio(false);
      setSnack({ msg: "Twilio configuration saved.", severity: "success" });
    } catch {
      setSnack({ msg: "Could not save Twilio configuration. Please try again.", severity: "error" });
    } finally {
      setSaving(false);
    }
  };

  const handleSaveUser = async (userId: string) => {
    setSaving(true);
    try {
      await updateSalonUser(organizationId, userId, {
        username: userForm.username,
        password: userForm.password, // blank = leave unchanged
      });
      await invalidate();
      setEditingUserId(null);
      setSnack({ msg: "User updated.", severity: "success" });
    } catch (error) {
      setSnack({
        msg:
          getHttpStatus(error) === 409
            ? "That username is already taken."
            : "Could not update the user. Please try again.",
        severity: "error",
      });
    } finally {
      setSaving(false);
    }
  };

  const timezoneOptions = TIMEZONES.includes(profileForm.timezone)
    ? TIMEZONES
    : [profileForm.timezone, ...TIMEZONES];

  return (
    <AnimatedPage>
      <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/Admin")} sx={{ mb: 1 }}>
          All salons
        </Button>

        {/* Profile */}
        <Paper variant="outlined" sx={{ p: SPACING.section, mb: 2 }}>
          <PageHeader
            title={salon.name}
            subtitle="Salon profile and SMS reminders"
            action={
              !editingProfile ? (
                <Button startIcon={<EditIcon />} onClick={startEditProfile}>
                  Edit
                </Button>
              ) : undefined
            }
          />

          {!editingProfile ? (
            <Stack divider={<Divider flexItem />}>
              <ReadRow label="Salon name" value={salon.name} />
              <ReadRow label="Business phone" value={salon.businessPhone || "—"} />
              <ReadRow label="Timezone" value={salon.timezone} />
              <ReadRow
                label="SMS reminders"
                value={
                  !twilioConfigured
                    ? "Unavailable (Twilio not configured)"
                    : salon.smsRemindersEnabled
                      ? "On"
                      : "Off"
                }
              />
            </Stack>
          ) : (
            <>
              <Stack spacing={2} sx={{ mb: 1 }}>
                <TextField
                  label="Salon name"
                  fullWidth
                  size="small"
                  value={profileForm.name}
                  onChange={(e) => setProfileForm((p) => ({ ...p, name: e.target.value }))}
                />
                <TextField
                  label="Business phone"
                  fullWidth
                  size="small"
                  value={profileForm.businessPhone}
                  onChange={(e) => {
                    const next = formatPhoneInput(e.target.value, profileForm.businessPhone);
                    if (next !== null) setProfileForm((p) => ({ ...p, businessPhone: next }));
                  }}
                />
                <TextField
                  select
                  label="Timezone"
                  fullWidth
                  size="small"
                  value={profileForm.timezone}
                  onChange={(e) => setProfileForm((p) => ({ ...p, timezone: e.target.value }))}
                >
                  {timezoneOptions.map((tz) => (
                    <MenuItem key={tz} value={tz}>
                      {tz}
                    </MenuItem>
                  ))}
                </TextField>
                <FormControlLabel
                  control={
                    <Switch
                      checked={twilioConfigured && profileForm.smsRemindersEnabled}
                      disabled={!twilioConfigured}
                      onChange={(e) => setProfileForm((p) => ({ ...p, smsRemindersEnabled: e.target.checked }))}
                    />
                  }
                  label={twilioConfigured ? "Send SMS appointment reminders" : "SMS reminders (configure Twilio first)"}
                />
              </Stack>
              <EditActions
                saving={saving}
                onCancel={() => setEditingProfile(false)}
                onSave={handleSaveProfile}
              />
            </>
          )}
        </Paper>

        {/* Twilio */}
        <Paper variant="outlined" sx={{ p: SPACING.section, mb: 2 }}>
          <SectionHeader
            title="Twilio configuration"
            chip={
              <Chip
                size="small"
                label={twilioConfigured ? "Configured" : "Not configured"}
                color={twilioConfigured ? "success" : "default"}
                variant={twilioConfigured ? "filled" : "outlined"}
              />
            }
            onEdit={!editingTwilio ? startEditTwilio : undefined}
          />

          {!editingTwilio ? (
            <Stack divider={<Divider flexItem />}>
              <ReadRow label="Account SID" value={twilio.accountSid || "—"} />
              <ReadRow label="Sending phone number" value={twilio.phoneNumber || "—"} />
              <ReadRow label="Auth token" value={twilioConfigured ? "•••••••• (set)" : "Not set"} />
            </Stack>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                The auth token is write-only — it's never shown. Leave it blank to keep the current one.
              </Typography>
              <Stack spacing={2}>
                <TextField
                  label="Account SID"
                  fullWidth
                  size="small"
                  value={twilioForm.accountSid}
                  onChange={(e) => setTwilioForm((t) => ({ ...t, accountSid: e.target.value }))}
                />
                <TextField
                  label="Sending phone number"
                  fullWidth
                  size="small"
                  value={twilioForm.phoneNumber}
                  onChange={(e) => setTwilioForm((t) => ({ ...t, phoneNumber: e.target.value }))}
                />
                <TextField
                  label="Auth token"
                  type="password"
                  fullWidth
                  size="small"
                  value={twilioForm.authToken}
                  placeholder={twilioConfigured ? "Leave blank to keep current" : "Enter the Twilio auth token"}
                  onChange={(e) => setTwilioForm((t) => ({ ...t, authToken: e.target.value }))}
                />
              </Stack>
              <EditActions saving={saving} onCancel={() => setEditingTwilio(false)} onSave={handleSaveTwilio} />
            </>
          )}
        </Paper>

        {/* Users */}
        <Paper variant="outlined" sx={{ p: SPACING.section }}>
          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
            Users
          </Typography>
          <Stack divider={<Divider flexItem />}>
            {users.map((user) => (
              <Box key={user.id} sx={{ py: 1 }}>
                {editingUserId !== user.id ? (
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                      <Typography fontWeight={500} noWrap>
                        {user.username}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {user.role}
                      </Typography>
                    </Box>
                    <Button
                      size="small"
                      startIcon={<EditIcon />}
                      disabled={editingUserId !== null}
                      onClick={() => startEditUser(user.id, user.username)}
                    >
                      Edit
                    </Button>
                  </Box>
                ) : (
                  <Stack spacing={2} sx={{ py: 1 }}>
                    <Typography variant="body2" color="text.secondary">
                      Editing {user.role} login
                    </Typography>
                    <TextField
                      label="Username"
                      fullWidth
                      size="small"
                      autoComplete="off"
                      value={userForm.username}
                      onChange={(e) => setUserForm((u) => ({ ...u, username: e.target.value }))}
                    />
                    <TextField
                      label="New password"
                      type="password"
                      fullWidth
                      size="small"
                      autoComplete="new-password"
                      placeholder="Leave blank to keep current"
                      value={userForm.password}
                      onChange={(e) => setUserForm((u) => ({ ...u, password: e.target.value }))}
                    />
                    <EditActions
                      saving={saving}
                      onCancel={() => setEditingUserId(null)}
                      onSave={() => handleSaveUser(user.id)}
                    />
                  </Stack>
                )}
              </Box>
            ))}
          </Stack>
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

function SectionHeader({
  title,
  chip,
  onEdit,
}: {
  title: string;
  chip?: ReactNode;
  onEdit?: () => void;
}) {
  return (
    <Stack direction="row" alignItems="center" sx={{ mb: 1.5 }}>
      <Typography variant="subtitle1" fontWeight={700}>
        {title}
      </Typography>
      {chip && <Box sx={{ ml: 1.5 }}>{chip}</Box>}
      <Box sx={{ flexGrow: 1 }} />
      {onEdit && (
        <Button startIcon={<EditIcon />} onClick={onEdit}>
          Edit
        </Button>
      )}
    </Stack>
  );
}

function EditActions({
  saving,
  onCancel,
  onSave,
}: {
  saving: boolean;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end", gap: 1 }}>
      <Button onClick={onCancel} disabled={saving}>
        Cancel
      </Button>
      <Button
        variant="contained"
        onClick={onSave}
        disabled={saving}
        startIcon={saving ? <CircularProgress size={16} color="inherit" /> : undefined}
        sx={{ minWidth: 100 }}
      >
        {saving ? "Saving…" : "Save"}
      </Button>
    </Box>
  );
}
