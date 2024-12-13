import { useState } from "react";
import AppointmentCalendar from "./components/CustomCalendar/CustomCalendar";
import CalendarHeader from "./components/CalendarHeader";
import { Stack, Box, Paper, Typography } from "@mui/material";
import CalendarNavigator from "./components/CalendarNavigator";
import ReminderButton from "./components/ReminderButton";
import { useTheme } from "@mui/material/styles";
import dayjs from "dayjs";

const CalendarClient = () => {
  const theme = useTheme();
  const [startDate, setStartDate] = useState(dayjs());

  const handleDateChange = (days: number) => {
    setStartDate(dayjs(startDate).add(days, "day"));
  };

  return (
    <Box
      sx={{
        padding: 4,
      }}
    >
      <Paper variant="outlined">
        <Stack padding={3} spacing={2}>
          <Stack spacing={2}>
            <Stack
              sx={{
                backgroundColor: theme.palette.primary.main,
                padding: 2,
                borderRadius: 2,
              }}
            >
              <Typography sx={{ color: "white" }} variant="h4">
                Appointments
              </Typography>
            </Stack>
            <Stack direction="row" spacing={2}>
              <Stack spacing={2}>
                <CalendarNavigator
                  startDate={startDate}
                  setStartDate={setStartDate}
                />
                <ReminderButton />
              </Stack>
              <Stack spacing={2} sx={{ flex: 1 }}>
                <CalendarHeader
                  startDate={startDate}
                  onDateChange={handleDateChange}
                />
                <AppointmentCalendar startDate={startDate} />
              </Stack>
            </Stack>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
};

export default CalendarClient;
