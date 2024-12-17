import { Button, Typography, Stack } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import dayjs from "dayjs";

interface CalendarHeaderProps {
  startDate: dayjs.Dayjs;
  onDateChange: (days: number) => void;
}

const CalendarHeader: React.FC<CalendarHeaderProps> = ({
  startDate,
  onDateChange,
}) => {
  const today = new Date(startDate.toString());
  return (
    <Stack
      direction="row"
      spacing={2}
      sx={{
        width: "100%",
      }}
    >
      <Button
        variant="contained"
        size="small"
        onClick={() => onDateChange(-1)}
        startIcon={<ArrowBackIcon />}
      ></Button>
      <Button
        variant="contained"
        size="small"
        onClick={() => onDateChange(1)}
        startIcon={<ArrowForwardIcon />}
      ></Button>
      <Typography>
        {today.toLocaleDateString("en-us", {
          weekday: "short",
          year: "numeric",
          month: "long",
          day: "numeric",
        })}
      </Typography>
    </Stack>
  );
};

export default CalendarHeader;
