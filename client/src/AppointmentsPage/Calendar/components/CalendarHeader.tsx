import { useState } from "react";
import {
  Typography,
  Stack,
  IconButton,
  Button,
  Popover,
} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import TodayIcon from "@mui/icons-material/Today";
import { DateCalendar, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";

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

  const isToday = startDate.isSame(dayjs(), "day");

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
    </>
  );
};

export default CalendarHeader;
