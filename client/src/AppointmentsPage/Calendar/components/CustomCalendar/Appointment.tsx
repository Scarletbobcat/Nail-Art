import { Appointment } from "../../../../types";
import { Stack, Typography, Box } from "@mui/material";

export default function CustomAppointment({
  appointment,
  onEventClick,
}: {
  appointment: Appointment;
  onEventClick?: (e: {
    originalEvent: React.MouseEvent;
    e: Appointment;
  }) => void;
}) {
  return (
    <Box
      onMouseUp={(event) =>
        onEventClick &&
        onEventClick({
          originalEvent: event,
          e: appointment,
        })
      }
    >
      <Stack>
        <Typography sx={{ fontSize: "0.8rem" }}>{appointment.name}</Typography>
        {appointment.services.map((service) => {
          return (
            <Typography
              key={service}
              sx={{
                fontSize: "0.7rem",
              }}
            >
              {service}
            </Typography>
          );
        })}
        <Typography
          sx={{
            fontSize: "0.7rem",
          }}
        >
          {appointment.phoneNumber}
        </Typography>
      </Stack>
    </Box>
  );
}
