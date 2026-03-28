import { useState } from "react";
import AppointmentCalendar from "./components/CustomCalendar/CustomCalendar";
import CalendarHeader from "./components/CalendarHeader";
import { Box, Paper, useMediaQuery, useTheme } from "@mui/material";
import dayjs from "dayjs";
import MobileCalendar from "./components/MobileCalendar";
import PageHeader from "../../components/PageHeader";
import { SPACING, MAX_CONTENT_WIDTH } from "../../constants/design";

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

  const onDateSet = (date: dayjs.Dayjs) => {
    setStartDate(date);
    localStorage.setItem("startDate", date.format("YYYY-MM-DD"));
  };

  return (
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Appointments"
          subtitle={startDate.format("dddd, MMMM D, YYYY")}
        />
        {isMobile ? (
          <MobileCalendar
            startDate={startDate}
            onDateChange={handleDateChange}
            onDateSet={onDateSet}
          />
        ) : (
          <>
            <CalendarHeader
              startDate={startDate}
              onDateChange={handleDateChange}
              onDateSet={onDateSet}
            />
            <AppointmentCalendar startDate={startDate} />
          </>
        )}
      </Paper>
    </Box>
  );
};

export default CalendarClient;
