import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Chip,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";

import AnimatedPage from "../components/AnimatedPage";
import PageHeader from "../components/PageHeader";
import PageSkeleton from "../components/PageSkeleton";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";
import { listSalons } from "../api/admin";

export const adminSalonsQueryKey = ["admin-salons"] as const;

export default function Organizations() {
  const navigate = useNavigate();
  const { data, isLoading, isError } = useQuery({
    queryKey: adminSalonsQueryKey,
    queryFn: listSalons,
    refetchOnWindowFocus: false,
  });

  if (isLoading) {
    return <PageSkeleton />;
  }

  return (
    <AnimatedPage>
      <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
        <PageHeader
          title="Salons"
          subtitle="Manage every organization, its profile, and its Twilio configuration"
          action={
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => navigate("/Admin/new")}
            >
              New salon
            </Button>
          }
        />

        {isError || !data ? (
          <Alert severity="error">Could not load salons. Check your connection and refresh.</Alert>
        ) : data.length === 0 ? (
          <Paper variant="outlined" sx={{ p: SPACING.section, textAlign: "center" }}>
            <Typography color="text.secondary">No salons yet. Create the first one.</Typography>
          </Paper>
        ) : (
          <Stack spacing={1.5}>
            {data.map((salon) => (
              <Paper
                key={salon.id}
                variant="outlined"
                onClick={() => navigate(`/Admin/${salon.id}`)}
                sx={{
                  p: SPACING.card,
                  display: "flex",
                  alignItems: "center",
                  gap: 2,
                  cursor: "pointer",
                  transition: "background-color 120ms",
                  "&:hover": { backgroundColor: "action.hover" },
                }}
              >
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography fontWeight={700} noWrap>
                    {salon.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {salon.timezone}
                    {salon.businessPhone ? ` · ${salon.businessPhone}` : ""}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                  <Chip
                    size="small"
                    label={salon.twilioConfigured ? "Twilio set" : "No Twilio"}
                    color={salon.twilioConfigured ? "success" : "default"}
                    variant={salon.twilioConfigured ? "filled" : "outlined"}
                  />
                  <Chip
                    size="small"
                    label={salon.smsRemindersEnabled ? "SMS on" : "SMS off"}
                    color={salon.smsRemindersEnabled ? "primary" : "default"}
                    variant={salon.smsRemindersEnabled ? "filled" : "outlined"}
                  />
                </Stack>
                <ChevronRightIcon sx={{ color: "text.disabled", flexShrink: 0 }} />
              </Paper>
            ))}
          </Stack>
        )}
      </Box>
    </AnimatedPage>
  );
}
