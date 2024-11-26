import { useState, useMemo } from "react";
import { DayPilotCalendar, DayPilot } from "@daypilot/daypilot-lite-react";
import CalendarHeader from "./components/CalendarHeader";
import { Stack } from "@mui/material";
import EditModal from "./components/EditModal";
import { getAppointmentsByDate } from "../../api/appointments";
import { getAllServices } from "../../api/services";
import { getAllEmployees } from "../../api/employees";
import { useQuery } from "@tanstack/react-query";
import CalendarNavigator from "./components/CalendarNavigator";
import { Appointment, Employee } from "../../types";
import CreateModal from "./components/CreateModal";
import DeleteModal from "./components/DeleteModal";
import ContextMenu from "./components/ContextMenu";
import CircularLoading from "../../components/CircularLoading";
import ReminderButton from "./components/ReminderButton";

const CalendarClient = () => {
  const [startDate, setStartDate] = useState(DayPilot.Date.today());
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [selectedAppId, setSelectedAppId] = useState("");
  const [createApp, setCreateApp] = useState<Appointment>({
    id: "",
    name: "",
    phoneNumber: "",
    date: "",
    startTime: "",
    endTime: "",
    employeeId: "",
    services: [],
  });
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
  } | null>(null);

  const handleContextMenu = (event: MouseEvent) => {
    event.preventDefault();
    setContextMenu(
      contextMenu === null
        ? {
            mouseX: event.clientX + 2,
            mouseY: event.clientY - 6,
          }
        : // repeated contextmenu when it is already open closes it with Chrome 84 on Ubuntu
          // Other native context menus might behave different.
          // With this behavior we prevent contextmenu from the backdrop to re-locale existing context menus.
          null
    );
  };

  const handleMenuClose = () => {
    setContextMenu(null);
  };

  const {
    data: appointments,
    isLoading: appLoading,
    error: appError,
    refetch: appRefetch,
  } = useQuery({
    queryKey: ["appointments", startDate],
    queryFn: () => getAppointmentsByDate(startDate.toString().substring(0, 10)),
  });

  const {
    data: services,
    isLoading: servicesLoading,
    error: servicesError,
  } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(),
  });

  const {
    data: employees,
    isLoading: employeesLoading,
    error: employeesError,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(),
  });

  // this will make sure that this is only run when appointments or employees changes
  // to reduce the amount of work done each time the calendar is re-rendered
  const mappedEvents = useMemo(() => {
    if (!appointments || !employees) return [];
    interface MappedEvent {
      id: string;
      resource: string;
      start: string;
      end: string;
      text: string;
      barColor: string;
    }

    return appointments.map((e: Appointment): MappedEvent => {
      const employee = employees.find(
        (emp: Employee) => emp.id === e.employeeId
      );
      const servicesList = e.services.join("\n");
      const employeeColor = employee ? employee.color : "#000000";
      return {
        id: e.id,
        resource: e.employeeId,
        start: e.date + e.startTime,
        end: e.date + e.endTime,
        text: `${e.name}\n${servicesList}\n${e.phoneNumber}`,
        barColor: employeeColor,
      };
    });
  }, [appointments, employees]);

  const handleDateChange = (days: number) => {
    setStartDate((prevDate) => prevDate.addDays(days));
  };

  // if there are any errors, show them
  if (appError) {
    return <div>Error fetching appointments</div>;
  }
  if (servicesError) {
    return <div>Error fetching services</div>;
  }
  if (employeesError) {
    return <div>Error fetching employees</div>;
  }

  return (
    <div>
      <ContextMenu
        anchorPosition={
          contextMenu !== null
            ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
            : undefined
        }
        open={contextMenu !== null}
        setEdit={setIsEditOpen}
        setDelete={setIsDeleteOpen}
        onClose={handleMenuClose}
      />
      <Stack padding={3} spacing={2}>
        <CalendarHeader startDate={startDate} onDateChange={handleDateChange} />
        {appLoading || servicesLoading || employeesLoading ? (
          <CircularLoading />
        ) : (
          <Stack
            direction="row"
            spacing={2}
            sx={{
              display: "flex",
            }}
          >
            <Stack spacing={2}>
              <CalendarNavigator
                startDate={startDate}
                setStartDate={setStartDate}
              />
              <ReminderButton />
            </Stack>
            <DayPilotCalendar
              viewType="Resources"
              columns={employees.sort((a: Employee, b: Employee) =>
                a.id.toString().localeCompare(b.id.toString())
              )}
              startDate={startDate}
              eventMoveHandling="Disabled"
              events={mappedEvents}
              businessBeginsHour={10}
              businessEndsHour={19}
              eventResizeHandling="Disabled"
              onEventClick={(args) => {
                // setIsEditOpen(true);
                // setIsDeleteOpen(true);
                setSelectedAppId(args.e.id().toString());
                handleContextMenu(args.originalEvent);
              }}
              onTimeRangeSelected={(args) => {
                setCreateApp({
                  ...createApp,
                  date: args.start.toString("yyyy-MM-dd"),
                  startTime: "T" + args.start.toString("HH:mm:ss"),
                  endTime: "T" + args.end.toString("HH:mm:ss"),
                  employeeId: args.resource.toString(),
                });
                setIsCreateOpen(true);
              }}
            />
          </Stack>
        )}
      </Stack>
      {isEditOpen && (
        <EditModal
          appointment={appointments.find(
            (app: Appointment) => app.id == selectedAppId
          )}
          isOpen={isEditOpen}
          onClose={() => setIsEditOpen(false)}
          renderEvents={appRefetch}
          allServices={services}
          allEmployees={employees}
        />
      )}
      {isCreateOpen && (
        <CreateModal
          appointment={createApp}
          isOpen={isCreateOpen}
          onClose={() => setIsCreateOpen(false)}
          renderEvents={appRefetch}
          allServices={services}
          allEmployees={employees}
        />
      )}
      {isDeleteOpen && (
        <DeleteModal
          appointment={appointments.find(
            (app: Appointment) => app.id == selectedAppId
          )}
          isOpen={isDeleteOpen}
          onClose={() => setIsDeleteOpen(false)}
          renderEvents={appRefetch}
          allEmployees={employees}
        />
      )}
    </div>
  );
};

export default CalendarClient;
