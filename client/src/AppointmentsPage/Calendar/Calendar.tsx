import { useState } from "react";
import AppointmentCalendar from "./components/CustomCalendar/CustomCalendar";
import CalendarHeader from "./components/CalendarHeader";
import { Stack, Box, Paper, useMediaQuery, useTheme } from "@mui/material";
import CalendarNavigator from "./components/CalendarNavigator";
import ReminderButton from "./components/ReminderButton";
import dayjs from "dayjs";
import TodayButton from "./components/TodayButton";
import MobileCalendar from "./components/MobileCalendar";
import PageHeader from "../../components/PageHeader";
import { SPACING } from "../../constants/design";

const CalendarClient = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
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
    <Box sx={{ p: SPACING.page }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Appointments"
          subtitle={startDate.format("dddd, MMMM D, YYYY")}
        />
        {isMobile ? (
          <MobileCalendar
            startDate={startDate}
            onDateChange={handleDateChange}
            onDateSet={(date) => {
              setStartDate(date);
              localStorage.setItem("startDate", date.format("YYYY-MM-DD"));
            }}
          />
        ) : (
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
        )}
      </Paper>
    </Box>
  );
};

export default CalendarClient;
