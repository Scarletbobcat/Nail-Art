import React from "react";
import { DayPilot } from "@daypilot/daypilot-lite-react";
import { Button, Typography, Box, Stack } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";

interface CalendarHeaderProps {
  startDate: DayPilot.Date;
  onDateChange: (days: number) => void;
}

const CalendarHeader: React.FC<CalendarHeaderProps> = ({
  startDate,
  onDateChange,
}) => {
  const today = new Date(startDate.toString());
  return (
    <Box display="flex" justifyContent="center" alignItems="center">
      <Stack direction="row" spacing={2}>
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
    </Box>
  );
};

export default CalendarHeader;
