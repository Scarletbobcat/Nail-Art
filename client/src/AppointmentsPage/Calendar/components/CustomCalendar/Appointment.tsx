import { Appointment } from "../../../../types";
import { Stack, Typography, Box } from "@mui/material";

export default function CustomAppointment({
  appointment,
}: {
  appointment: Appointment;
}) {
  return (
    <Box>
      <Stack>
        <Typography sx={{ fontSize: "0.8rem" }}>{appointment.name}</Typography>
        {appointment.services.map((service) => {
          return (
            <Typography
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
