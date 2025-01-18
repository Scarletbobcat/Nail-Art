import { Typography, Paper, Box } from "@mui/material";
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

const businessTimes = Array.from({ length: 11 }, (_, i) => {
  const hour = i + 9;
  if (hour >= 12) {
    return `${hour % 12 || 12} PM`;
  }
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
    // refetch: refetchClients,
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

  if (employeeError) {
    return <Typography>Error loading employees</Typography>;
  }

  if (appointmentsError) {
    return <Typography>Error loading appointments</Typography>;
  }

  if (servicesError) {
    return <Typography>Error loading services</Typography>;
  }

  if (clientsError) {
    return <Typography>Error loading clients</Typography>;
  }

  return (
    <div>
      <Box
        sx={{
          height: "calc(100vh - 64px)",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Box
          sx={{
            display: "flex",
            flex: 1,
            overflow: "auto",
          }}
        >
          {/* Time labels column */}
          <Box
            sx={{
              width: "60px",
            }}
          >
            <Paper
              variant="outlined"
              sx={{
                height: "50px",
                position: "sticky",
                top: 0,
                zIndex: 2,
                borderRadius: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                backgroundColor: "white",
              }}
            >
              <Typography variant="subtitle2">Time</Typography>
            </Paper>
            {businessTimes.map((time, index) =>
              index !== businessTimes.length - 1 ? (
                <Paper
                  key={time}
                  variant="outlined"
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: "white",
                    borderTop: "none",
                    borderRadius: 0,
                    height: 80,
                  }}
                >
                  <Typography variant="subtitle2">{time}</Typography>
                </Paper>
              ) : null
            )}
          </Box>

          {/* Employee columns */}
          <Box
            sx={{
              display: "flex",
              flex: 1,
            }}
          >
            {employees.map((employee: Employee) => (
              <Box
                key={employee.id}
                sx={{
                  flex: 1,
                }}
              >
                <Paper
                  variant="outlined"
                  sx={{
                    height: "50px",
                    position: "sticky",
                    top: 0,
                    zIndex: 2,
                    borderRadius: 0,
                    borderLeft: "none",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: "white",
                  }}
                >
                  <Typography variant="subtitle1">{employee.name}</Typography>
                </Paper>
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
                  businessStart={9}
                  onTimeRangeSelected={onTimeRangeSelected}
                  startDate={startDate}
                  services={services}
                />
              </Box>
            ))}
          </Box>
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
    </div>
  );
}
