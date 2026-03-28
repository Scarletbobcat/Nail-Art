import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import dayjs from "dayjs";
import { DateCalendar } from "@mui/x-date-pickers/DateCalendar";
import { Paper } from "@mui/material";

export default function CalendarNavigator({
  startDate,
  setStartDate,
}: {
  startDate: dayjs.Dayjs;
  setStartDate: (date: dayjs.Dayjs) => void;
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        "& .MuiDateCalendar-root": {
          width: "100%",
        },
      }}
    >
      <LocalizationProvider dateAdapter={AdapterDayjs} dateLibInstance={dayjs}>
        <DateCalendar
          value={dayjs(startDate)}
          onChange={(date) => {
            if (date) setStartDate(date);
          }}
        />
      </LocalizationProvider>
    </Paper>
  );
}
