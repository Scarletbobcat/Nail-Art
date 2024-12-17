import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import dayjs from "dayjs";
import { DateCalendar } from "@mui/x-date-pickers/DateCalendar";

export default function CalendarNavigator({
  startDate,
  setStartDate,
}: {
  startDate: dayjs.Dayjs;
  setStartDate: (date: dayjs.Dayjs) => void;
}) {
  return (
    <div>
      {/* <DayPilotNavigator
        selectMode="Day"
        showMonths={1}
        startDate={startDate}
        selectionDay={startDate}
        onTimeRangeSelected={(args: { day: DayPilot.Date }) =>
          setStartDate(args.day)
        }
      /> */}
      <LocalizationProvider dateAdapter={AdapterDayjs} dateLibInstance={dayjs}>
        <DateCalendar
          value={dayjs(startDate)}
          onChange={(date) => setStartDate(date)}
        />
      </LocalizationProvider>
    </div>
  );
}
