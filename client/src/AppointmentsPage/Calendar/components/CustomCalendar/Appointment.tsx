import { Appointment, Service } from "../../../../types";
import { Typography, Box, Tooltip, Stack } from "@mui/material";
import { formatTime } from "../../../../utils/datetime";

export default function CustomAppointment({
  appointment,
  onEventClick,
  isSpecial = false,
  services = [],
  orgTz,
  bgcolor,
}: {
  appointment: Appointment;
  onEventClick?: (e: {
    originalEvent: React.MouseEvent;
    e: Appointment;
  }) => void;
  services?: Service[];
  isSpecial?: boolean;
  orgTz: string;
  bgcolor?: string;
}) {
  const startTime = formatTime(appointment.startsAt, orgTz).replace(/\s[AP]M$/, "");
  const endTime = formatTime(appointment.endsAt, orgTz);

  const textColor = isSpecial ? "white" : "text.primary";
  const secondaryColor = isSpecial ? "rgba(255,255,255,0.8)" : "text.secondary";

  const serviceNames = appointment.services
    .map((id) => services.find((s) => s.id === id)?.name)
    .filter(Boolean)
    .join(", ");

  const tooltipContent = (
    <Stack spacing={0.5} sx={{ p: 0.5 }}>
      <Typography variant="subtitle2" fontWeight={600}>
        {appointment.name}
      </Typography>
      <Typography variant="caption">
        {startTime} – {endTime}
      </Typography>
      {serviceNames && (
        <Typography variant="caption">{serviceNames}</Typography>
      )}
      {appointment.phoneNumber && (
        <Typography variant="caption">
          {appointment.phoneNumber}
        </Typography>
      )}
    </Stack>
  );

  return (
    <Tooltip title={tooltipContent} arrow placement="right" followCursor>
      <Box
        data-testid="desktop-appointment"
        onMouseUp={(event) =>
          onEventClick &&
          onEventClick({ originalEvent: event, e: appointment })
        }
        sx={{ height: "100%", width: "100%", bgcolor }}
      >
        <Typography
          sx={{
            fontSize: "0.78rem",
            fontWeight: 600,
            lineHeight: 1.3,
            color: textColor,
          }}
          noWrap
        >
          {appointment.name}
        </Typography>
        {serviceNames && (
          <Typography
            sx={{
              fontSize: "0.68rem",
              lineHeight: 1.3,
              color: secondaryColor,
            }}
            noWrap
          >
            {serviceNames}
          </Typography>
        )}
        <Typography
          sx={{
            fontSize: "0.65rem",
            lineHeight: 1.3,
            color: secondaryColor,
          }}
          noWrap
        >
          {startTime} – {endTime}
        </Typography>
        {appointment.phoneNumber && (
          <Typography
            sx={{
              fontSize: "0.65rem",
              lineHeight: 1.3,
              color: secondaryColor,
            }}
            noWrap
          >
            {appointment.phoneNumber}
          </Typography>
        )}
      </Box>
    </Tooltip>
  );
}
