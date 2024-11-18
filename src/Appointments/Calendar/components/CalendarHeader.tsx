import React from "react";
import { DayPilot } from "@daypilot/daypilot-lite-react";
import { Button, Typography, Box } from "@mui/material";
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
      <Typography>{"Date: " + today.toDateString()}</Typography>
      <Box display="flex">
        <Button
          variant="contained"
          size="small"
          onClick={() => onDateChange(-1)}
          startIcon={<ArrowBackIcon />}
        ></Button>
        <Typography variant="h6">Day</Typography>
        <Button
          variant="contained"
          size="small"
          onClick={() => onDateChange(1)}
          startIcon={<ArrowForwardIcon />}
        ></Button>
      </Box>
    </Box>
  );
};

export default CalendarHeader;
