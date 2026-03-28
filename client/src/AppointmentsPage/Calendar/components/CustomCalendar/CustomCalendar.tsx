import { Typography, Box } from "@mui/material";
import dayjs from "dayjs";
import TimeSlotGrid from "./TimeSlots";
import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../../../../api/employees";
import { Employee, Appointment } from "../../../../types";
import CircularLoading from "../../../../components/CircularLoading";
import { useState } from "react";
import {
  createAppointment,
  deleteAppointment,
  editAppointment,
  getAppointmentsByDate,
} from "../../../../api/appointments";
import { getAllServices } from "../../../../api/services";
import { getClients } from "../../../../api/clients";
import ContextMenu from "./ContextMenu";
import AppointmentModal from "../../../components/AppointmentModal";

const HOUR_HEIGHT = 64;
const HEADER_HEIGHT = 44;
const TIME_COL_WIDTH = 56;
const START_HOUR = 9;
const END_HOUR = 21;
const TOTAL_HOURS = END_HOUR - START_HOUR;

const businessTimes = Array.from({ length: TOTAL_HOURS }, (_, i) => {
  const hour = i + START_HOUR;
  if (hour >= 12) return `${hour % 12 || 12} PM`;
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
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [selectedAppId, setSelectedAppId] = useState("");
  const [createApp, setCreateApp] = useState<Appointment>({
    id: "",
    name: null,
    phoneNumber: "",
    date: "",
    startTime: "",
    endTime: "",
    employeeId: "",
    services: [],
    reminderSent: false,
    showedUp: false,
  });
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
  } | null>(null);

  const {
    data: employees,
    isLoading: employeeLoading,
    error: employeeError,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(),
  });

  const {
    data: appointments,
    error: appointmentsError,
    isLoading: appointmentsLoading,
    refetch: appRefetch,
  } = useQuery({
    queryKey: ["appointments", startDate],
    queryFn: () => getAppointmentsByDate(startDate.format("YYYY-MM-DD")),
  });

  const {
    data: services,
    error: servicesError,
    isLoading: servicesLoading,
  } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(),
  });

  const {
    data: clients,
    error: clientsError,
    isLoading: clientsLoading,
  } = useQuery({
    queryKey: ["clients"],
    queryFn: () => getClients({}),
  });

  const handleContextMenu = (event: React.MouseEvent) => {
    event.preventDefault();
    setContextMenu(
      contextMenu === null
        ? {
            mouseX: event.clientX + 2,
            mouseY: event.clientY - 6,
          }
        : null
    );
  };

  const handleMenuClose = () => {
    setContextMenu(null);
  };

  const onTimeRangeSelected = (e: TimeRangeEvent) => {
    setCreateApp({
      ...createApp,
      date: startDate.format("YYYY-MM-DD"),
      startTime: "T" + e.startTime,
      endTime: "T" + e.endTime,
      employeeId: e.employee,
    });
    setIsCreateOpen(true);
  };

  if (
    employeeLoading ||
    appointmentsLoading ||
    servicesLoading ||
    clientsLoading
  ) {
    return <CircularLoading />;
  }

  if (employeeError || appointmentsError || servicesError || clientsError) {
    return <Typography color="error">Error loading data</Typography>;
  }

  return (
    <Box>
      <Box
        sx={{
          height: "calc(100vh - 240px)",
          minHeight: 400,
          display: "flex",
          flexDirection: "column",
          border: "1px solid",
          borderColor: "divider",
          borderRadius: 2,
          overflow: "hidden",
          bgcolor: "background.paper",
        }}
      >
        {/* Sticky header row */}
        <Box
          sx={{
            display: "flex",
            borderBottom: "1px solid",
            borderColor: "divider",
            bgcolor: "background.default",
            flexShrink: 0,
          }}
        >
          {/* Time column header */}
          <Box
            sx={{
              width: TIME_COL_WIDTH,
              flexShrink: 0,
              height: HEADER_HEIGHT,
            }}
          />
          {/* Employee headers */}
          {employees.map((employee: Employee) => (
            <Box
              key={employee.id}
              sx={{
                flex: 1,
                minWidth: 0,
                height: HEADER_HEIGHT,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 1,
                borderLeft: "1px solid",
                borderColor: "divider",
              }}
            >
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  bgcolor: employee.color || "primary.main",
                  flexShrink: 0,
                }}
              />
              <Typography
                variant="body2"
                fontWeight={600}
                color="text.primary"
                noWrap
              >
                {employee.name}
              </Typography>
            </Box>
          ))}
        </Box>

        {/* Scrollable grid */}
        <Box
          sx={{
            flex: 1,
            overflow: "auto",
            display: "flex",
          }}
        >
          {/* Time labels */}
          <Box
            sx={{
              width: TIME_COL_WIDTH,
              flexShrink: 0,
            }}
          >
            {businessTimes.map((time) => (
              <Box
                key={time}
                sx={{
                  height: HOUR_HEIGHT,
                  display: "flex",
                  alignItems: "flex-start",
                  justifyContent: "flex-end",
                  pr: 1,
                  pt: 0.25,
                }}
              >
                <Typography
                  variant="caption"
                  sx={{
                    fontSize: "0.7rem",
                    color: "text.secondary",
                    lineHeight: 1,
                    fontWeight: 500,
                  }}
                >
                  {time}
                </Typography>
              </Box>
            ))}
          </Box>

          {/* Employee columns */}
          {employees.map((employee: Employee) => (
            <Box
              key={employee.id}
              sx={{
                flex: 1,
                minWidth: 0,
                position: "relative",
              }}
            >
              <TimeSlotGrid
                onEventClick={(args: {
                  originalEvent: React.MouseEvent;
                  e: Appointment;
                }) => {
                  handleContextMenu(args.originalEvent);
                  setSelectedAppId(args.e.id);
                }}
                appointments={appointments}
                employee={employee}
                businessStart={START_HOUR}
                businessEnd={END_HOUR}
                hourHeight={HOUR_HEIGHT}
                onTimeRangeSelected={onTimeRangeSelected}
                startDate={startDate}
                services={services}
              />
            </Box>
          ))}
        </Box>
      </Box>

      {isCreateOpen && (
        <AppointmentModal
          appointment={createApp}
          isOpen={isCreateOpen}
          onClose={() => setIsCreateOpen(false)}
          type="create"
          onSubmit={createAppointment}
          renderEvents={appRefetch}
          allServices={services}
          allEmployees={employees}
          clients={clients}
        />
      )}
      <ContextMenu
        anchorPosition={
          contextMenu !== null
            ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
            : undefined
        }
        appointment={appointments.find(
          (app: Appointment) => app.id == selectedAppId
        )}
        renderEvents={appRefetch}
        editShowedUp={async () => {
          const appointment = appointments.find(
            (app: Appointment) => app.id == selectedAppId
          );
          if (appointment) {
            await editAppointment({
              ...appointment,
              showedUp: !appointment.showedUp,
            });
          }
        }}
        open={contextMenu !== null}
        setEdit={setIsEditOpen}
        setDelete={setIsDeleteOpen}
        onClose={handleMenuClose}
      />
      {isEditOpen && (
        <AppointmentModal
          appointment={appointments.find(
            (app: Appointment) => app.id == selectedAppId
          )}
          isOpen={isEditOpen}
          type={"edit"}
          onSubmit={editAppointment}
          onClose={() => setIsEditOpen(false)}
          renderEvents={appRefetch}
          allServices={services}
          allEmployees={employees}
        />
      )}
      {isDeleteOpen && (
        <AppointmentModal
          allServices={services}
          appointment={appointments.find(
            (app: Appointment) => app.id == selectedAppId
          )}
          onSubmit={deleteAppointment}
          isOpen={isDeleteOpen}
          onClose={() => setIsDeleteOpen(false)}
          renderEvents={appRefetch}
          allEmployees={employees}
          type={"delete"}
        />
      )}
    </Box>
  );
}
