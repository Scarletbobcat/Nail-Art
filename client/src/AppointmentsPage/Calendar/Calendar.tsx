import { useState } from "react";
import AppointmentCalendar from "./components/CustomCalendar/CustomCalendar";
import CalendarHeader from "./components/CalendarHeader";
import { Stack, Box, Paper, Typography } from "@mui/material";
import CalendarNavigator from "./components/CalendarNavigator";
import ReminderButton from "./components/ReminderButton";
import { useTheme } from "@mui/material/styles";
import dayjs from "dayjs";
import TodayButton from "./components/TodayButton";

const CalendarClient = () => {
  const theme = useTheme();
  const [startDate, setStartDate] = useState(
    localStorage.getItem("startDate")
      ? dayjs(localStorage.getItem("startDate"))
      : dayjs()
  );

  const handleDateChange = (days: number) => {
    setStartDate(dayjs(startDate).add(days, "day"));
    localStorage.setItem(
      "startDate",
      dayjs(startDate).add(days, "day").format("YYYY-MM-DD")
    );
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
                <TodayButton
                  onClick={() => {
                    setStartDate(dayjs());
                    localStorage.setItem(
                      "startDate",
                      dayjs().format("YYYY-MM-DD")
                    );
                  }}
                />
                <CalendarNavigator
                  startDate={startDate}
                  setStartDate={(date: dayjs.Dayjs) => {
                    setStartDate(date);
                    localStorage.setItem(
                      "startDate",
                      date.format("YYYY-MM-DD")
                    );
                  }}
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
