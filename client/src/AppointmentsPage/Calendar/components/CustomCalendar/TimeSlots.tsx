import { Box } from "@mui/material";
import { Employee, Appointment } from "../../../../types";
import { useCallback, useState } from "react";
import dayjs from "dayjs";
import CustomAppointment from "./Appointment";

interface TimeSlotGridProps {
  employee: Employee;
  appointments: Appointment[];
  onTimeRangeSelected?: (e: TimeRangeEvent) => void;
  startDate: dayjs.Dayjs;
  businessStart?: number;
  businessEnd?: number;
  onEventClick?: (e: {
    originalEvent: React.MouseEvent;
    e: Appointment;
  }) => void;
}

interface TimeRangeEvent {
  startTime: string;
  endTime: string;
  employee: string;
}

const TimeSlotGrid = ({
  employee,
  appointments,
  onEventClick,
  onTimeRangeSelected,
  businessStart = 10,
  businessEnd = 19,
}: TimeSlotGridProps) => {
  const [isSelecting, setIsSelecting] = useState(false);
  const [selectedSlots, setSelectedSlots] = useState<number[]>([]);
  const [selectionStart, setSelectionStart] = useState<number | null>(null);

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

  const isSlotOverlapped = useCallback(
    (slotTime: string) => {
      if (!appointments) return false;
      return appointments.some((app) => {
        if (app.employeeId !== employee.id) return false;

        const arbitraryDate = "2024-12-13";

        const slotDateTime = dayjs(`${arbitraryDate} ${slotTime}`);
        const appStart = dayjs(
          `${arbitraryDate} ${app.startTime.split("T")[1]}`
        );
        const appEnd = dayjs(`${arbitraryDate} ${app.endTime.split("T")[1]}`);

        return slotDateTime.isAfter(appStart) && slotDateTime.isBefore(appEnd);
      });
    },
    [appointments, employee.id]
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
    >
      <Box
        sx={{
          position: "absolute",
          left: 0,
          right: 0,
        }}
      >
        {timeSlots.map((slot, index) => {
          const time = `${slot.hour}:${slot.minute
            .toString()
            .padStart(2, "0")}`;
          const isDisabled = isSlotOverlapped(time);
          return (
            <Box
              key={`${slot.hour}:${slot.minute}-${slot.employee}`}
              onMouseDown={
                isDisabled ? undefined : () => handleMouseDown(index)
              }
              onMouseEnter={
                isDisabled ? undefined : () => handleMouseEnter(index)
              }
              onMouseUp={
                isDisabled
                  ? undefined
                  : () => {
                      const timeRangeEvent = handleMouseUp();
                      if (onTimeRangeSelected) {
                        onTimeRangeSelected(timeRangeEvent);
                      }
                    }
              }
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
          );
        })}
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
              <CustomAppointment
                appointment={appointment}
                key={appointment.id}
                onEventClick={onEventClick}
              />
            </Box>
          ))}
    </Box>
  );
};

export default TimeSlotGrid;
