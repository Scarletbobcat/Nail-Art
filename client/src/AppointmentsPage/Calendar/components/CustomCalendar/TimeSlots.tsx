import { Box } from "@mui/material";
import { Employee, Appointment } from "../../../../types";
import { useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAppointmentsByDate } from "../../../../api/appointments";
import dayjs from "dayjs";
import CustomAppointment from "./Appointment";

interface TimeSlotGridProps {
  employee: Employee;
  onTimeRangeSelected?: (e: TimeRangeEvent) => void;
  startDate: dayjs.Dayjs;
  businessStart?: number;
  businessEnd?: number;
}

interface TimeRangeEvent {
  startTime: string;
  endTime: string;
  employee: string;
}

const TimeSlotGrid = ({
  employee,
  startDate,
  onTimeRangeSelected,
  businessStart = 10,
  businessEnd = 19,
}: TimeSlotGridProps) => {
  const [isSelecting, setIsSelecting] = useState(false);
  const [selectedSlots, setSelectedSlots] = useState<number[]>([]);
  const [selectionStart, setSelectionStart] = useState<number | null>(null);

  const {
    data: appointments,
    isLoading: appointmentsLoading,
    error: appointmentsError,
  } = useQuery({
    queryKey: ["appointments", startDate],
    queryFn: () => getAppointmentsByDate(startDate.format("YYYY-MM-DD")),
  });
  const timeSlots = Array.from(
    { length: (businessEnd - businessStart) * 4 },
    (_, i) => {
      const hour = Math.floor(i / 4) + businessStart;
      const minute = (i % 4) * 15; // Calculate minutes in 15-min intervals
      return {
        hour: hour,
        minute: minute,
        employee: employee.id,
        index: i,
      };
    }
  );

  const handleMouseDown = useCallback((index: number) => {
    setIsSelecting(true);
    setSelectionStart(index);
    setSelectedSlots([index]);
  }, []);

  const handleMouseEnter = useCallback(
    (index: number) => {
      if (!isSelecting || selectionStart === null) return;

      const start = Math.min(selectionStart, index);
      const end = Math.max(selectionStart, index);
      const slots = Array.from(
        { length: end - start + 1 },
        (_, i) => start + i
      );
      setSelectedSlots(slots);
    },
    [isSelecting, selectionStart]
  );

  const getPositionFromTime = (time: string) => {
    time = time.split("T")[1];
    const [hour, minutes] = time.split(":").map(Number);
    const totalMinutes = (hour - businessStart) * 60 + minutes;
    console.log(totalMinutes);
    return (totalMinutes / 30) * 40; // 40px is height of each slot
  };

  const handleMouseUp = useCallback(() => {
    setIsSelecting(false);
    setSelectionStart(null);
    // Handle selected slots here
    const cells = timeSlots
      .map((timeslot) => {
        if (selectedSlots.includes(timeslot.index)) {
          return timeslot;
        }
      })
      .filter((slot) => slot !== undefined);
    const event: TimeRangeEvent = {
      employee: employee.id,
      startTime: `${cells[0].hour}:${cells[0].minute || "00"}`,
      endTime: `${timeSlots[cells[cells.length - 1].index + 1].hour}:${
        timeSlots[cells[cells.length - 1].index + 1].minute || "00"
      }`,
    };
    setSelectedSlots([]);
    return event;
  }, [selectedSlots, timeSlots, employee]);

  return (
    <Box
      sx={{
        height: "600px",
        position: "relative",
      }}
      onMouseUp={() => {
        const timeRangeEvent = handleMouseUp();
        if (onTimeRangeSelected) {
          onTimeRangeSelected(timeRangeEvent);
        }
      }}
    >
      <Box
        sx={{
          position: "absolute",
          left: 0,
          right: 0,
        }}
      >
        {timeSlots.map((slot, index) => (
          <Box
            key={`${slot.hour}:${slot.minute}-${slot.index}`}
            onMouseDown={() => handleMouseDown(index)}
            onMouseEnter={() => handleMouseEnter(index)}
            sx={{
              border: ".5px solid #e0e0e0",
              borderBottom:
                (index + 1) % 4 === 0 ? "1.5px solid #e0e0e0" : "none",
              borderLeft: "none",
              height: 20,
              minHeight: 20,
              backgroundColor: selectedSlots.includes(slot.index)
                ? "#f1f1f1"
                : "background.paper",
              // "&:hover": {
              //   backgroundColor: isSelecting ? "#f1f1f1" : "action.hover",
              // },
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              position: "relative",
            }}
          />
        ))}
      </Box>
      {/* Render appointments */}
      {appointments &&
        appointments
          .filter((app: Appointment) => app.employeeId === employee.id)
          .map((appointment: Appointment) => (
            <Box
              key={appointment.id}
              sx={{
                position: "absolute",
                left: 0,
                right: 0,
                top: getPositionFromTime(appointment.startTime),
                height:
                  getPositionFromTime(appointment.endTime) -
                  getPositionFromTime(appointment.startTime),
                backgroundColor: employee.color || "primary.main",
                opacity: 0.9,
                zIndex: 1,
                border: "1px solid",
                paddingX: 1,
                color: "white",
                fontSize: "0.875rem",
                overflow: "hidden",
                cursor: "pointer",
                "&:hover": {
                  opacity: 1,
                },
              }}
            >
              <CustomAppointment appointment={appointment} />
            </Box>
          ))}
    </Box>
  );
};

export default TimeSlotGrid;
