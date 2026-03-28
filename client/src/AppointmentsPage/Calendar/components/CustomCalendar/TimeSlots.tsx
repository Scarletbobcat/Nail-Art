import { Box } from "@mui/material";
import { Employee, Appointment, Service } from "../../../../types";
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
  hourHeight?: number;
  onEventClick?: (e: {
    originalEvent: React.MouseEvent;
    e: Appointment;
  }) => void;
  services: Service[];
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
  businessStart = 9,
  businessEnd = 21,
  hourHeight = 64,
  services,
}: TimeSlotGridProps) => {
  const [isSelecting, setIsSelecting] = useState(false);
  const [selectedSlots, setSelectedSlots] = useState<number[]>([]);
  const [selectionStart, setSelectionStart] = useState<number | null>(null);

  const totalSlots = (businessEnd - businessStart) * 4;

  const timeSlots = Array.from({ length: totalSlots }, (_, i) => {
    const hour = Math.floor(i / 4) + businessStart;
    const minute = (i % 4) * 15;
    return { hour, minute, employee: employee.id, index: i };
  });

  const slotHeight = hourHeight / 4;

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
      setSelectedSlots(
        Array.from({ length: end - start + 1 }, (_, i) => start + i)
      );
    },
    [isSelecting, selectionStart]
  );

  const getPositionFromTime = (time: string) => {
    time = time.split("T")[1];
    const [hour, minutes] = time.split(":").map(Number);
    const totalMinutes = (hour - businessStart) * 60 + minutes;
    return (totalMinutes / 60) * hourHeight;
  };

  const handleMouseUp = useCallback(() => {
    setIsSelecting(false);
    setSelectionStart(null);
    const cells = timeSlots
      .filter((slot) => selectedSlots.includes(slot.index));
    if (cells.length === 0) {
      setSelectedSlots([]);
      return null;
    }
    const startTime = `${
      cells[0].hour < 10 ? "0" + cells[0].hour : cells[0].hour
    }:${cells[0].minute || "00"}`;
    const lastIndex = cells[cells.length - 1].index;
    const endTime = timeSlots[lastIndex + 1];
    const event: TimeRangeEvent = {
      employee: employee.id,
      startTime: startTime,
      endTime: endTime
        ? `${endTime.hour}:${endTime.minute || "00"}`
        : `${cells[cells.length - 1].hour + 1}:00`,
    };
    setSelectedSlots([]);
    return event;
  }, [selectedSlots, timeSlots, employee]);

  return (
    <Box
      sx={{
        position: "relative",
        borderLeft: "1px solid",
        borderColor: "divider",
      }}
    >
      {/* Time slot grid */}
      {timeSlots.map((slot, index) => {
        const time = `${slot.hour}:${slot.minute
          .toString()
          .padStart(2, "0")}`;
        const isDisabled = isSlotOverlapped(time);
        const isHourBoundary = index % 4 === 0;
        const isHalfHour = index % 4 === 2;
        const isSelected = selectedSlots.includes(slot.index);

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
                    if (timeRangeEvent && onTimeRangeSelected) {
                      onTimeRangeSelected(timeRangeEvent);
                    }
                  }
            }
            sx={{
              height: slotHeight,
              borderTop: isHourBoundary
                ? "1px solid"
                : isHalfHour
                  ? "1px dashed"
                  : "none",
              borderColor: isHourBoundary
                ? "divider"
                : "rgba(226, 232, 240, 0.5)",
              backgroundColor: isSelected
                ? "rgba(59, 130, 246, 0.08)"
                : "transparent",
              cursor: isDisabled ? "default" : "pointer",
              "&:hover": isDisabled
                ? {}
                : { backgroundColor: isSelected ? "rgba(59, 130, 246, 0.08)" : "rgba(0, 0, 0, 0.015)" },
            }}
          />
        );
      })}

      {/* Appointment blocks */}
      {appointments &&
        appointments
          .filter((app: Appointment) => app.employeeId === employee.id)
          .map((appointment: Appointment) => {
            const isServiceType3 = appointment.services.includes(3);
            const isShowedUp = appointment.showedUp;
            const isSpecial = isServiceType3 || isShowedUp;

            let blockColor = employee.color || "#3b82f6";
            if (isServiceType3) blockColor = "#000000";
            if (isShowedUp) blockColor = "#666666";

            return (
              <Box
                key={appointment.id}
                sx={{
                  position: "absolute",
                  left: 4,
                  right: 4,
                  top: getPositionFromTime(appointment.startTime),
                  height:
                    getPositionFromTime(appointment.endTime) -
                    getPositionFromTime(appointment.startTime) -
                    1,
                  bgcolor: isSpecial ? blockColor : `${blockColor}14`,
                  borderLeft: `3px solid ${blockColor}`,
                  borderRadius: "4px",
                  px: 1,
                  py: 0.25,
                  overflow: "hidden",
                  cursor: "pointer",
                  zIndex: 1,
                  opacity: isSpecial ? 0.85 : 1,
                  transition: "box-shadow 0.15s, transform 0.15s",
                  "&:hover": {
                    boxShadow: 2,
                    transform: "scale(1.01)",
                    opacity: 1,
                  },
                }}
              >
                <CustomAppointment
                  appointment={appointment}
                  onEventClick={onEventClick}
                  isSpecial={isSpecial}
                  services={services}
                />
              </Box>
            );
          })}
    </Box>
  );
};

export default TimeSlotGrid;
