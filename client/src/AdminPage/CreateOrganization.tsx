import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";

import AnimatedPage from "../components/AnimatedPage";
import PageHeader from "../components/PageHeader";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";
import { getHttpStatus } from "../utils/httpError";
import { formatPhoneInput } from "../utils/phone";
import { createSalon, type CreateSalonResponse } from "../api/admin";
import { adminSalonsQueryKey } from "./Organizations";

const TIMEZONES = [
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Phoenix",
  "America/Los_Angeles",
  "America/Anchorage",
  "Pacific/Honolulu",
];

export default function CreateOrganization() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [form, setForm] = useState({
    name: "",
    timezone: "America/New_York",
    businessPhone: "",
    ownerUsername: "",
    ownerPassword: "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<CreateSalonResponse | null>(null);

  const update = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }));

  const canSubmit =
    form.name.trim() && form.ownerUsername.trim() && form.ownerPassword.trim() && !saving;

  const handleSubmit = async () => {
    setError("");
    setSaving(true);
    try {
      const result = await createSalon({
        name: form.name.trim(),
        timezone: form.timezone,
        businessPhone: form.businessPhone || undefined,
        ownerUsername: form.ownerUsername.trim(),
        ownerPassword: form.ownerPassword,
      });
      await queryClient.invalidateQueries({ queryKey: adminSalonsQueryKey });
      setCreated(result);
    } catch (err) {
      setError(
        getHttpStatus(err) === 409
          ? "That salon name or owner username already exists."
          : "Could not create the salon. Please check the fields and try again."
      );
    } finally {
      setSaving(false);
    }
  };

  if (created) {
    return (
      <AnimatedPage>
        <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
          <Paper variant="outlined" sx={{ p: SPACING.section }}>
            <Stack spacing={1.5} alignItems="flex-start">
              <Stack direction="row" spacing={1} alignItems="center">
                <CheckCircleIcon color="success" />
                <Typography variant="h6" fontWeight={700}>
                  {created.name} created
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Share these owner credentials with the salon. The password is not stored in plain
                text and can't be shown again.
              </Typography>
              <Box
                sx={{
                  width: "100%",
                  bgcolor: "action.hover",
                  borderRadius: 1,
                  p: 2,
                  fontFamily: "monospace",
                  fontSize: 14,
                }}
              >
                <div>Owner username: {created.ownerUsername}</div>
                <div>Owner password: {form.ownerPassword}</div>
              </Box>
              <Stack direction="row" spacing={1.5} sx={{ pt: 1 }}>
                <Button variant="contained" onClick={() => navigate(`/Admin/${created.organizationId}`)}>
                  Configure Twilio
                </Button>
                <Button onClick={() => navigate("/Admin")}>Back to salons</Button>
              </Stack>
            </Stack>
          </Paper>
        </Box>
      </AnimatedPage>
    );
  }

  return (
    <AnimatedPage>
      <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/Admin")} sx={{ mb: 1 }}>
          All salons
        </Button>
        <Paper variant="outlined" sx={{ p: SPACING.section }}>
          <PageHeader title="New salon" subtitle="Create an organization and its first owner login" />

          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
            Salon
          </Typography>
          <Stack spacing={2} sx={{ mb: 3 }}>
            <TextField
              label="Salon name"
              required
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
              {TIMEZONES.map((tz) => (
                <MenuItem key={tz} value={tz}>
                  {tz}
                </MenuItem>
              ))}
            </TextField>
          </Stack>

          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
            First owner login
          </Typography>
          <Stack spacing={2}>
            <TextField
              label="Owner username"
              required
              fullWidth
              size="small"
              autoComplete="off"
              value={form.ownerUsername}
              onChange={(e) => update({ ownerUsername: e.target.value })}
            />
            <TextField
              label="Owner password"
              required
              fullWidth
              size="small"
              type="text"
              autoComplete="off"
              value={form.ownerPassword}
              onChange={(e) => update({ ownerPassword: e.target.value })}
            />
          </Stack>

          {error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {error}
            </Alert>
          )}

          <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
            <Button
              variant="contained"
              onClick={handleSubmit}
              disabled={!canSubmit}
              startIcon={saving ? <CircularProgress size={16} color="inherit" /> : undefined}
              sx={{ minWidth: 140 }}
            >
              {saving ? "Creating…" : "Create salon"}
            </Button>
          </Box>
        </Paper>
      </Box>
    </AnimatedPage>
  );
}
