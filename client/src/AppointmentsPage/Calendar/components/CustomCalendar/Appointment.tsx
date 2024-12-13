import { Appointment } from "../../../../types";
import {
  Stack,
  Typography,
  Box,
  Tooltip,
  ListItem,
  List,
  ListItemText,
} from "@mui/material";
import dayjs from "dayjs";

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
  const tooltipContent = (
    <Stack spacing={1}>
      <Typography variant="subtitle1">{appointment.name}</Typography>
      <Typography variant="caption">
        Time:{" "}
        {dayjs(
          `${appointment.date} ${appointment.startTime.split("T")[1]}`
        ).format("HH:mm")}{" "}
        -
        {dayjs(
          `${appointment.date} ${appointment.endTime.split("T")[1]}`
        ).format("HH:mm")}
      </Typography>
      <Typography variant="caption">Services:</Typography>
      <ul>
        {appointment.services.map((service) => (
          <li key={service}>{service}</li>
        ))}
      </ul>
      <Typography variant="caption">
        Phone: {appointment.phoneNumber}
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
    </Tooltip>
  );
}
