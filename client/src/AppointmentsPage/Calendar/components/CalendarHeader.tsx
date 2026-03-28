import { useState } from "react";
import {
  Typography,
  Stack,
  IconButton,
  Button,
  Popover,
  Snackbar,
  Alert,
  CircularProgress,
} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import TodayIcon from "@mui/icons-material/Today";
import NotificationsNoneIcon from "@mui/icons-material/NotificationsNone";
import { DateCalendar, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import { remindAppointments } from "../../../api/appointments";

interface CalendarHeaderProps {
  startDate: dayjs.Dayjs;
  onDateChange: (days: number) => void;
  onDateSet: (date: dayjs.Dayjs) => void;
}

const CalendarHeader: React.FC<CalendarHeaderProps> = ({
  startDate,
  onDateChange,
  onDateSet,
}) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [remindLoading, setRemindLoading] = useState(false);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });

  const isToday = startDate.isSame(dayjs(), "day");

  const handleRemind = async () => {
    try {
      setRemindLoading(true);
      const message = await remindAppointments();
      setSnackbar({ open: true, message, severity: "success" });
    } catch (error) {
      const err = error as {
        response?: { data?: string };
        message?: string;
      };
      setSnackbar({
        open: true,
        message:
          err.response?.data || err.message || "Failed to send reminders",
        severity: "error",
      });
    } finally {
      setRemindLoading(false);
    }
  };

  return (
    <>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ mb: 2 }}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <IconButton onClick={() => onDateChange(-1)} size="small">
            <ChevronLeftIcon />
          </IconButton>
          <IconButton onClick={() => onDateChange(1)} size="small">
            <ChevronRightIcon />
          </IconButton>
          <Typography
            variant="h6"
            fontWeight={600}
            onClick={(e) => setAnchorEl(e.currentTarget)}
            sx={{
              pl: 1,
              cursor: "pointer",
              borderBottom: "1px dashed",
              borderColor: "text.secondary",
              "&:hover": { color: "primary.main", borderColor: "primary.main" },
              transition: "color 0.15s, border-color 0.15s",
            }}
          >
            {startDate.format("dddd, MMMM D, YYYY")}
          </Typography>
        </Stack>

        <Stack direction="row" spacing={1}>
          <Button
            variant={isToday ? "contained" : "outlined"}
            size="small"
            startIcon={<TodayIcon />}
            onClick={() => onDateSet(dayjs())}
          >
            Today
          </Button>
          <Button
            variant="outlined"
            size="small"
            startIcon={
              remindLoading ? (
                <CircularProgress size={16} color="inherit" />
              ) : (
                <NotificationsNoneIcon />
              )
            }
            disabled={remindLoading}
            onClick={handleRemind}
          >
            {remindLoading ? "Sending..." : "Send Reminders"}
          </Button>
        </Stack>
      </Stack>

      <Popover
        open={!!anchorEl}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
        transformOrigin={{ vertical: "top", horizontal: "left" }}
      >
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <DateCalendar
            value={startDate}
            onChange={(date) => {
              if (date) {
                onDateSet(date);
                setAnchorEl(null);
              }
            }}
          />
        </LocalizationProvider>
      </Popover>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
          variant="filled"
          sx={{ width: "100%", borderRadius: 2 }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export default CalendarHeader;
