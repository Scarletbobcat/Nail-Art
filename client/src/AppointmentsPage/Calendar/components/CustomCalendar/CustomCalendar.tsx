import { Typography, Paper, Box } from "@mui/material";
import dayjs from "dayjs";
import TimeSlotGrid from "./TimeSlots";
import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../../../../api/employees";
import { Employee } from "../../../../types";
import CircularLoading from "../../../../components/CircularLoading";

const businessTimes = Array.from({ length: 10 }, (_, i) => {
  const hour = i + 10;
  if (hour >= 12) {
    return `${hour % 12 || 12} PM`;
  }
  return `${hour} AM`;
});

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
  const {
    data: employees,
    isLoading: employeeLoading,
    error: employeeError,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(),
  });

  const onTimeRangeSelected = (e: TimeRangeEvent) => {
    console.log(e);
  };

  if (employeeLoading) {
    return <CircularLoading />;
  }

  if (employeeError) {
    return <Typography>Error loading employees</Typography>;
  }

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
          {employees.map((employee: Employee) => (
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
                startDate={startDate}
              />
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}
