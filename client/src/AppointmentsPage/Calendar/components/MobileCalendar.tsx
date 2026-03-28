import { useState, useRef, useEffect } from "react";
import {
  Stack,
  Box,
  Typography,
  Chip,
  SwipeableDrawer,
  Fab,
  CircularProgress,
  Snackbar,
  Alert,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import AccessTimeIcon from "@mui/icons-material/AccessTime";
import PersonIcon from "@mui/icons-material/Person";
import CheckIcon from "@mui/icons-material/Check";
import EventIcon from "@mui/icons-material/Event";
import TodayIcon from "@mui/icons-material/Today";
import NotificationsNoneIcon from "@mui/icons-material/NotificationsNone";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { getAllEmployees } from "../../../api/employees";
import {
  getAppointmentsByDate,
  createAppointment,
  deleteAppointment,
  editAppointment,
  remindAppointments,
} from "../../../api/appointments";
import { getAllServices } from "../../../api/services";
import { getClients } from "../../../api/clients";
import { Appointment, Employee, Service } from "../../../types";
import CircularLoading from "../../../components/CircularLoading";
import EmployeeSelector from "./EmployeeSelector";
import MobileDateHeader from "./MobileDateHeader";
import AppointmentModal from "../../components/AppointmentModal";

const HOUR_HEIGHT = 80;
const START_HOUR = 9;
const END_HOUR = 21;
const TOTAL_HOURS = END_HOUR - START_HOUR;
const TIME_LABEL_WIDTH = 52;

export default function MobileCalendar({
  startDate,
  onDateChange,
  onDateSet,
}: {
  startDate: dayjs.Dayjs;
  onDateChange: (days: number) => void;
  onDateSet: (date: dayjs.Dayjs) => void;
}) {
  const navigate = useNavigate();
  const [selectedEmployeeId, setSelectedEmployeeId] = useState("");
  const [detailApp, setDetailApp] = useState<Appointment | null>(null);
  const [checkLoading, setCheckLoading] = useState(false);
  const [selectedApp, setSelectedApp] = useState<Appointment | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [createApp, setCreateApp] = useState<Appointment>({
    id: "", name: null, phoneNumber: "", date: "", startTime: "", endTime: "",
    employeeId: "", services: [], reminderSent: false, showedUp: false,
  });
  const [remindLoading, setRemindLoading] = useState(false);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });
  const scrollRef = useRef<HTMLDivElement>(null);

  const { data: employees, isLoading: empLoading } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(),
  });
  const { data: appointments, isLoading: appLoading, refetch: appRefetch } = useQuery({
    queryKey: ["appointments", startDate],
    queryFn: () => getAppointmentsByDate(startDate.format("YYYY-MM-DD")),
  });
  const { data: services, isLoading: svcLoading } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(),
  });
  const { data: clients, isLoading: clientsLoading } = useQuery({
    queryKey: ["clients"],
    queryFn: () => getClients({}),
  });

  const activeEmpId = selectedEmployeeId || employees?.[0]?.id || "";
  const activeEmp = employees?.find((e: Employee) => e.id === activeEmpId);
  const empColor = activeEmp?.color || "#3b82f6";

  const empAppointments = (appointments || []).filter(
    (a: Appointment) => a.employeeId === activeEmpId
  );

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, [activeEmpId, startDate]);



  const handleAppointmentTap = (app: Appointment, e: React.MouseEvent) => {
    e.stopPropagation();
    setDetailApp(app);
  };

  const getServiceNames = (serviceIds: number[]) =>
    serviceIds
      .map((id) => services?.find((s: Service) => s.id === id)?.name)
      .filter(Boolean)
      .join(", ");

  const [now, setNow] = useState(dayjs());
  useEffect(() => {
    const id = setInterval(() => setNow(dayjs()), 60_000);
    return () => clearInterval(id);
  }, []);

  if (empLoading || appLoading || svcLoading || clientsLoading) return <CircularLoading />;

  // Current time indicator position
  const isToday = startDate.isSame(now, "day");
  const nowOffset = isToday
    ? ((now.hour() - START_HOUR) + now.minute() / 60) * HOUR_HEIGHT
    : -1;

  return (
    <Stack spacing={1.5}>
      <EmployeeSelector
        employees={employees || []}
        selectedId={activeEmpId}
        onSelect={setSelectedEmployeeId}
      />
      <MobileDateHeader startDate={startDate} onDateChange={onDateChange} onDateSet={onDateSet} />

      <Stack direction="row" spacing={1} justifyContent="center">
        <Chip
          icon={<TodayIcon sx={{ fontSize: 16 }} />}
          label="Today"
          size="small"
          variant={isToday ? "filled" : "outlined"}
          color={isToday ? "primary" : "default"}
          onClick={() => onDateSet(dayjs())}
          sx={{ fontWeight: 500, px: 1 }}
        />
        <Chip
          icon={
            remindLoading ? (
              <CircularProgress size={14} thickness={5} color="inherit" />
            ) : (
              <NotificationsNoneIcon sx={{ fontSize: 16 }} />
            )
          }
          label={remindLoading ? "Sending..." : "Send Reminders"}
          size="small"
          variant="outlined"
          disabled={remindLoading}
          onClick={async () => {
            try {
              setRemindLoading(true);
              const message = await remindAppointments();
              setSnackbar({ open: true, message, severity: "success" });
            } catch (error) {
              const err = error as { response?: { data?: string }; message?: string };
              setSnackbar({
                open: true,
                message: err.response?.data || err.message || "Failed to send reminders",
                severity: "error",
              });
            } finally {
              setRemindLoading(false);
            }
          }}
          sx={{ fontWeight: 500, px: 1 }}
        />
      </Stack>

      {/* Time grid */}
      <Box
        ref={scrollRef}
        sx={{
          position: "relative",
          height: "calc(100vh - 310px)",
          overflowY: "auto",
          overflowX: "hidden",
          borderRadius: 0,
          border: "none",
          borderTop: "1px solid",
          borderColor: "divider",
          overflowAnchor: "none",
          bgcolor: "background.paper",
        }}
      >
        <Box
          sx={{ position: "relative", height: TOTAL_HOURS * HOUR_HEIGHT }}
        >
          {/* Hour lines and labels */}
          {Array.from({ length: TOTAL_HOURS }, (_, i) => {
            const hour = START_HOUR + i;
            const label = hour === 12 ? "12 PM" : hour > 12 ? `${hour - 12} PM` : `${hour} AM`;
            return (
              <Box
                key={hour}
                sx={{
                  position: "absolute",
                  top: i * HOUR_HEIGHT,
                  left: 0,
                  right: 0,
                  height: HOUR_HEIGHT,
                  borderBottom: "1px solid",
                  borderColor: "divider",
                  display: "flex",
                }}
              >
                <Box
                  sx={{
                    width: TIME_LABEL_WIDTH,
                    flexShrink: 0,
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "flex-end",
                    pr: 1,
                    pt: 0.25,
                  }}
                >
                  <Typography
                    variant="caption"
                    sx={{ fontSize: "0.68rem", color: "text.secondary", lineHeight: 1 }}
                  >
                    {label}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    flex: 1,
                    borderLeft: "1px solid",
                    borderColor: "divider",
                    position: "relative",
                  }}
                >
                  {/* Quarter-hour lines */}
                  {[0.25, 0.5, 0.75].map((frac) => (
                    <Box
                      key={frac}
                      sx={{
                        position: "absolute",
                        top: `${frac * 100}%`,
                        left: 0,
                        right: 0,
                        borderTop: frac === 0.5 ? "1px dashed" : "1px dotted",
                        borderColor: frac === 0.5 ? "grey.300" : "grey.200",
                      }}
                    />
                  ))}
                </Box>
              </Box>
            );
          })}

          {/* Current time indicator */}
          {isToday && nowOffset >= 0 && nowOffset <= TOTAL_HOURS * HOUR_HEIGHT && (
            <Box
              sx={{
                position: "absolute",
                top: nowOffset,
                left: TIME_LABEL_WIDTH - 4,
                right: 0,
                zIndex: 3,
                display: "flex",
                alignItems: "center",
              }}
            >
              <Box sx={{ width: 8, height: 8, borderRadius: "50%", bgcolor: "error.main" }} />
              <Box sx={{ flex: 1, height: "2px", bgcolor: "error.main" }} />
            </Box>
          )}

          {/* Appointment blocks */}
          {empAppointments.map((app: Appointment) => {
            const start = dayjs(app.date + app.startTime);
            const end = dayjs(app.date + app.endTime);
            const topOffset = ((start.hour() - START_HOUR) + start.minute() / 60) * HOUR_HEIGHT;
            const duration = end.diff(start, "minute");
            const height = (duration / 60) * HOUR_HEIGHT;

            // Color priority: showedUp (grey) > service type 3 (black) > employee color
            let blockColor = empColor;
            const isServiceType3 = app.services.includes(3);
            if (isServiceType3) blockColor = "#000000";
            if (app.showedUp) blockColor = "#666666";
            const isSpecial = app.showedUp || isServiceType3;

            return (
              <Box
                key={app.id}
                onClick={(e) => handleAppointmentTap(app, e)}
                sx={{
                  position: "absolute",
                  top: topOffset + 1,
                  left: TIME_LABEL_WIDTH + 4,
                  right: 8,
                  height: height - 2,
                  bgcolor: isSpecial ? blockColor : `${blockColor}18`,
                  borderLeft: `3px solid ${blockColor}`,
                  borderRadius: "4px",
                  px: 1,
                  py: 0.5,
                  overflow: "hidden",
                  cursor: "pointer",
                  opacity: isSpecial ? 0.85 : 1,
                  transition: "box-shadow 0.15s",
                  "&:hover": { boxShadow: 2 },
                  "&:active": { opacity: 1 },
                  zIndex: 2,
                }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    fontSize: "0.8rem",
                    lineHeight: 1.2,
                    color: isSpecial ? "white" : "text.primary",
                  }}
                  noWrap
                >
                  {app.name}
                </Typography>
                {height > 30 && (
                  <Typography
                    variant="caption"
                    sx={{
                      color: isSpecial ? "rgba(255,255,255,0.8)" : "text.secondary",
                      fontSize: "0.7rem",
                      lineHeight: 1.3,
                      display: "block",
                    }}
                    noWrap
                  >
                    {start.format("h:mm")} – {end.format("h:mm A")}
                  </Typography>
                )}
                {height > 44 && (
                  <Typography
                    variant="caption"
                    sx={{
                      color: isSpecial ? "rgba(255,255,255,0.8)" : "text.secondary",
                      fontSize: "0.65rem",
                      lineHeight: 1.3,
                      display: "block",
                    }}
                    noWrap
                  >
                    {getServiceNames(app.services)}
                  </Typography>
                )}
                {height > 58 && app.phoneNumber && (
                  <Typography
                    variant="caption"
                    sx={{
                      color: isSpecial ? "rgba(255,255,255,0.7)" : "text.secondary",
                      fontSize: "0.65rem",
                      lineHeight: 1.3,
                      display: "block",
                    }}
                    noWrap
                  >
                    {app.phoneNumber}
                  </Typography>
                )}
              </Box>
            );
          })}
        </Box>
      </Box>

      {/* FAB for quick create */}
      <Fab
        color="primary"
        size="medium"
        onClick={() => {
          const hour = Math.max(START_HOUR, Math.min(now.hour(), END_HOUR - 1));
          const minute = Math.floor(now.minute() / 15) * 15;
          setCreateApp({
            ...createApp,
            date: startDate.format("YYYY-MM-DD"),
            startTime: `T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`,
            endTime: `T${String(Math.min(hour + 1, END_HOUR)).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`,
            employeeId: activeEmpId,
          });
          setIsCreateOpen(true);
        }}
        sx={{
          position: "fixed",
          bottom: 80,
          right: 20,
          zIndex: 10,
        }}
      >
        <AddIcon />
      </Fab>

      {/* Appointment Detail Bottom Sheet */}
      <SwipeableDrawer
        anchor="bottom"
        open={!!detailApp}
        onOpen={() => {}}
        onClose={() => setDetailApp(null)}
        disableSwipeToOpen
        sx={{
          "& .MuiDrawer-paper": {
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            maxHeight: "50vh",
          },
        }}
      >
        {detailApp && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ display: "flex", justifyContent: "center", mb: 2 }}>
              <Box sx={{ width: 32, height: 4, borderRadius: 2, bgcolor: "grey.300" }} />
            </Box>
            <Typography variant="h6" fontWeight={600} sx={{ mb: 1 }}>
              {detailApp.name}
            </Typography>
            <Stack spacing={0.75} sx={{ mb: 2 }}>
              {detailApp.phoneNumber && (
                <Stack direction="row" spacing={1} alignItems="center">
                  <PersonIcon sx={{ fontSize: 18, color: "text.secondary" }} />
                  <Typography variant="body2" color="text.secondary">
                    {detailApp.phoneNumber}
                  </Typography>
                </Stack>
              )}
              <Stack direction="row" spacing={1} alignItems="center">
                <AccessTimeIcon sx={{ fontSize: 18, color: "text.secondary" }} />
                <Typography variant="body2" color="text.secondary">
                  {dayjs(detailApp.date + detailApp.startTime).format("h:mm A")} – {dayjs(detailApp.date + detailApp.endTime).format("h:mm A")}
                </Typography>
              </Stack>
              <Stack direction="row" spacing={1} alignItems="center">
                <PersonIcon sx={{ fontSize: 18, color: "text.secondary" }} />
                <Typography variant="body2" color="text.secondary">
                  {employees?.find((e: Employee) => e.id === detailApp.employeeId)?.name}
                </Typography>
              </Stack>
            </Stack>
            <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mb: 2.5 }}>
              {detailApp.services.map((id) => {
                const name = services?.find((s: Service) => s.id === id)?.name;
                return name ? <Chip key={id} label={name} size="small" variant="outlined" /> : null;
              })}
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip
                label="Edit"
                color="primary"
                onClick={() => {
                  setSelectedApp(detailApp);
                  setDetailApp(null);
                  setIsEditOpen(true);
                }}
                sx={{ px: 2 }}
              />
              <Chip
                label="Delete"
                color="error"
                variant="outlined"
                onClick={() => {
                  setSelectedApp(detailApp);
                  setDetailApp(null);
                  setIsDeleteOpen(true);
                }}
                sx={{ px: 2 }}
              />
              {detailApp && !detailApp.services.includes(3) && (
                <Chip
                  icon={checkLoading ? <CircularProgress size={14} /> : <CheckIcon />}
                  label={detailApp.showedUp ? "Check Out" : "Check In"}
                  color={detailApp.showedUp ? "success" : "default"}
                  variant="outlined"
                  disabled={checkLoading}
                  onClick={async () => {
                    setCheckLoading(true);
                    await editAppointment({
                      ...detailApp,
                      showedUp: !detailApp.showedUp,
                    });
                    await appRefetch();
                    setCheckLoading(false);
                    setDetailApp(null);
                  }}
                  sx={{ px: 1 }}
                />
              )}
              {detailApp?.phoneNumber && (
                <Chip
                  icon={<EventIcon />}
                  label="All Appointments"
                  variant="outlined"
                  onClick={() => {
                    setDetailApp(null);
                    navigate(`/Appointments/Search?pn=${detailApp.phoneNumber}`);
                  }}
                  sx={{ px: 1 }}
                />
              )}
            </Stack>
          </Box>
        )}
      </SwipeableDrawer>

      {isCreateOpen && (
        <AppointmentModal
          isOpen={isCreateOpen}
          type="create"
          onSubmit={createAppointment}
          onClose={() => setIsCreateOpen(false)}
          appointment={createApp}
          renderEvents={() => appRefetch()}
          allEmployees={employees || []}
          allServices={services || []}
          clients={clients}
        />
      )}
      {isEditOpen && selectedApp && (
        <AppointmentModal
          isOpen={isEditOpen}
          type="edit"
          onSubmit={editAppointment}
          onClose={() => { setIsEditOpen(false); setSelectedApp(null); }}
          appointment={selectedApp}
          renderEvents={() => appRefetch()}
          allEmployees={employees || []}
          allServices={services || []}
        />
      )}
      {isDeleteOpen && selectedApp && (
        <AppointmentModal
          isOpen={isDeleteOpen}
          type="delete"
          onSubmit={deleteAppointment}
          onClose={() => { setIsDeleteOpen(false); setSelectedApp(null); }}
          appointment={selectedApp}
          renderEvents={() => appRefetch()}
          allEmployees={employees || []}
          allServices={services || []}
        />
      )}

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        sx={{ bottom: { xs: 80 } }}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
          variant="filled"
          sx={{ width: "100%", borderRadius: 2 }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Stack>
  );
}
