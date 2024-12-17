import { Appointment, Service } from "../../../../types";
import { Stack, Typography, Box, Tooltip } from "@mui/material";
import dayjs from "dayjs";

export default function CustomAppointment({
  appointment,
  onEventClick,
  services,
}: {
  appointment: Appointment;
  onEventClick?: (e: {
    originalEvent: React.MouseEvent;
    e: Appointment;
  }) => void;
  services: Service[];
}) {
  const tooltipContent = (
    <Stack spacing={1}>
      <Typography variant="subtitle1">{appointment.name}</Typography>
      <Typography variant="caption">
        Time:{" "}
        {dayjs(
          `${appointment.date} ${appointment.startTime.split("T")[1]}`
        ).format("h:mm")}{" "}
        -
        {dayjs(
          `${appointment.date} ${appointment.endTime.split("T")[1]}`
        ).format("h:mm")}
      </Typography>
      <Typography variant="caption">Services:</Typography>
      <ul>
        {appointment.services.map((service) => {
          const serviceName = services.find((s) => s.id == service)?.name;
          return <li key={service}>{serviceName}</li>;
        })}
      </ul>
      <Typography variant="caption">
        Phone: {appointment.phoneNumber ? appointment.phoneNumber : "N/A"}
      </Typography>
    </Stack>
  );
  return (
    <Tooltip title={tooltipContent} arrow placement="right" followCursor>
      <Box
        onMouseUp={(event) =>
          onEventClick &&
          onEventClick({
            originalEvent: event,
            e: appointment,
          })
        }
        sx={{
          height: "100%",
          width: "100%",
        }}
      >
        <Stack>
          <Typography sx={{ fontSize: "0.8rem" }}>
            {appointment.name}
          </Typography>
          {appointment.services.map((service) => {
            const serviceName = services.find((s) => s.id == service)?.name;
            return (
              <Typography
                key={service}
                sx={{
                  fontSize: "0.7rem",
                }}
              >
                {serviceName}
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
    </Tooltip>
  );
}
