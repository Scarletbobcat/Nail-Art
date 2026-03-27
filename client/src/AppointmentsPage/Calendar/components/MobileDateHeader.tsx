import { useState } from "react";
import { Stack, IconButton, Typography, Popover } from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { DateCalendar, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import { useSwipeable } from "react-swipeable";

export default function MobileDateHeader({
  startDate,
  onDateChange,
  onDateSet,
}: {
  startDate: dayjs.Dayjs;
  onDateChange: (days: number) => void;
  onDateSet: (date: dayjs.Dayjs) => void;
}) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const handlers = useSwipeable({
    onSwipedLeft: () => onDateChange(1),
    onSwipedRight: () => onDateChange(-1),
    preventScrollOnSwipe: true,
  });

  return (
    <>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        {...handlers}
        sx={{ py: 1, userSelect: "none" }}
      >
        <IconButton onClick={() => onDateChange(-1)}>
          <ChevronLeftIcon />
        </IconButton>
        <Typography
          variant="subtitle1"
          fontWeight={600}
          onClick={(e) => setAnchorEl(e.currentTarget)}
          sx={{
            cursor: "pointer",
            borderBottom: "1px dashed",
            borderColor: "text.secondary",
            "&:active": { opacity: 0.6 },
          }}
        >
          {startDate.format("ddd, MMM D, YYYY")}
        </Typography>
        <IconButton onClick={() => onDateChange(1)}>
          <ChevronRightIcon />
        </IconButton>
      </Stack>
      <Popover
        open={!!anchorEl}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        transformOrigin={{ vertical: "top", horizontal: "center" }}
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
}
