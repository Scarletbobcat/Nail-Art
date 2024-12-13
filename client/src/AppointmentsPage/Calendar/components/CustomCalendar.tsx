import { Typography, Paper, Box } from "@mui/material";
import dayjs from "dayjs";
import { Employee } from "../../../types";
import TimeSlotGrid from "./TimeSlots";

const businessTimes = Array.from({ length: 10 }, (_, i) => {
  const hour = i + 10;
  if (hour >= 12) {
    return `${hour % 12 || 12} PM`;
  }
  return `${hour} AM`;
});

const employees: Employee[] = [
  {
    id: "1",
    name: "John",
    color: "#FF0000",
  },
  {
    id: "2",
    name: "Jane",
    color: "#00FF00",
  },
  {
    id: "3",
    name: "Joe",
    color: "#0000FF",
  },
  {
    id: "4",
    name: "Jill",
    color: "#FFFF00",
  },
];

interface TimeRangeEvent {
  startTime: string;
  endTime: string;
  employee: string;
}

export default function AppointmentCalendar({
  startDate,
}: {
  startDate: dayjs.Dayjs;
}) {
  const onTimeRangeSelected = (e: TimeRangeEvent) => {
    console.log(e);
  };
  return (
    <Box
      sx={{
        height: "calc(100vh - 64px)",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <Box
        sx={{
          display: "flex",
          flex: 1,
          overflow: "auto",
        }}
      >
        {/* Time labels column */}
        <Box
          sx={{
            width: "60px",
          }}
        >
          <Paper
            variant="outlined"
            sx={{
              height: "50px",
              position: "sticky",
              top: 0,
              zIndex: 2,
              borderRadius: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              backgroundColor: "white",
            }}
          >
            <Typography variant="subtitle2">Time</Typography>
          </Paper>
          {businessTimes.map((time, index) =>
            index !== businessTimes.length - 1 ? (
              <Paper
                key={time}
                variant="outlined"
                sx={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  backgroundColor: "white",
                  borderTop: "none",
                  borderRadius: 0,
                  height: 80,
                }}
              >
                <Typography variant="subtitle2">{time}</Typography>
              </Paper>
            ) : null
          )}
        </Box>

        {/* Employee columns */}
        <Box
          sx={{
            display: "flex",
            flex: 1,
          }}
        >
          {employees.map((employee) => (
            <Box
              key={employee.id}
              sx={{
                flex: 1,
              }}
            >
              <Paper
                variant="outlined"
                sx={{
                  height: "50px",
                  position: "sticky",
                  top: 0,
                  zIndex: 2,
                  borderRadius: 0,
                  borderLeft: "none",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  backgroundColor: "white",
                }}
              >
                <Typography variant="subtitle1">{employee.name}</Typography>
              </Paper>
              <TimeSlotGrid
                employee={employee}
                businessStart={10}
                onTimeRangeSelected={onTimeRangeSelected}
              />
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}
