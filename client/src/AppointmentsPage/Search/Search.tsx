import { useState, useMemo, useEffect } from "react";
import { Box, TextField, Chip } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../../api/employees";
import {
  deleteAppointment,
  editAppointment,
  getAppointmentsByPhoneNumber,
} from "../../api/appointments";
import { Appointment, Employee, Service } from "../../types";
import { Stack, Paper } from "@mui/material";
import { getAllServices } from "../../api/services";
import PageSkeleton from "../../components/PageSkeleton";
import AnimatedPage from "../../components/AnimatedPage";
import AppointmentModal from "../components/AppointmentModal";
import CustomButton from "../../components/Button";
import { useSearchParams } from "react-router-dom";
import PageHeader from "../../components/PageHeader";
import CardList from "../../components/CardList";
import { SPACING, MAX_CONTENT_WIDTH } from "../../constants/design";

interface TempData {
  id: number;
  name: string;
  phoneNumber: string;
  startTime: string;
  endTime: string;
  date: string;
  employeeId: string;
  services: string[];
}

export default function Search() {
  const [searchParams] = useSearchParams();
  const phoneNumberParam = searchParams.get("pn") || "";
  const [phoneNumber, setPhoneNumber] = useState(phoneNumberParam);
  const [tempData, setTempData] = useState<TempData[]>();
  const [loading, setLoading] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [selectedApp, setSelectedApp] = useState<Appointment>({
    id: "",
    name: "",
    phoneNumber: "",
    startTime: "",
    endTime: "",
    date: "",
    employeeId: "",
    services: [],
    reminderSent: false,
    showedUp: false,
  });

  const {
    data: employees,
    isLoading: employeesLoading,
    error: employeesError,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(),
  });

  const {
    data: services,
    isLoading: servicesLoading,
    error: servicesError,
  } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(),
  });

  useEffect(() => {
    const fetchAppointments = async () => {
      if (phoneNumberParam && phoneNumberParam.trim() !== "") {
        setLoading(true);
        try {
          const appointmentsData = await getAppointmentsByPhoneNumber(
            phoneNumberParam
          );
          setTempData(appointmentsData);
        } catch (error) {
          console.error("Error fetching appointments:", error);
        } finally {
          setLoading(false);
        }
      }
    };

    fetchAppointments();
  }, [phoneNumberParam]);

  function changePhoneNumber(inputPhoneNumber: string) {
    const regex = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;
    if (regex.test(inputPhoneNumber)) {
      let newPN = inputPhoneNumber;
      if (
        (newPN.length === 3 && phoneNumber.length === 2) ||
        (newPN.length === 7 && phoneNumber.length === 6)
      ) {
        newPN += "-";
      }
      setPhoneNumber(newPN);
    }
  }

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      setLoading(true);
      try {
        const appointmentsData = await getAppointmentsByPhoneNumber(
          phoneNumber || phoneNumberParam
        );
        setTempData(appointmentsData);
      } catch (error) {
        console.error("Error fetching appointments:", error);
      } finally {
        setLoading(false);
      }
    }
  };

  const data = useMemo(() => {
    if (!tempData || !employees) return [];
    const sortedTempData = [...tempData].sort((a, b) => a.id - b.id);
    return sortedTempData.map((row) => {
      const employee = employees.find(
        (employee: Employee) => employee.id == row.employeeId
      );
      return {
        id: row.id,
        name: row.name,
        phoneNumber: row.phoneNumber,
        startTime: new Date(row.date + row.startTime).toLocaleTimeString(
          "en-US", { hour: "numeric", minute: "2-digit" }
        ),
        endTime: new Date(row.date + row.endTime).toLocaleTimeString(
          "en-US", { hour: "numeric", minute: "2-digit" }
        ),
        date: row.date,
        employee: employee ? employee.name : "unknown",
        services: row.services.map((service) => {
          const serviceName = services.find(
            (s: Service) => service == s.id?.toString()
          )?.name;
          return serviceName;
        }),
        actions: row,
      };
    });
  }, [tempData, employees, services]);

  if (employeesLoading || loading || servicesLoading) {
    return <PageSkeleton />;
  }

  if (employeesError) {
    return <div>Error fetching employees</div>;
  }
  if (servicesError) {
    return <div>Error fetching services</div>;
  }

  return (
    <AnimatedPage>
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Appointments"
          subtitle="Search appointments by phone number"
        />
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ mb: 2 }}
          alignItems={{ sm: "center" }}
        >
          <TextField
            size="small"
            label="Phone Number"
            onChange={(e) => changePhoneNumber(e.target.value)}
            onKeyDown={handleKeyDown}
            value={phoneNumber}
          />
          <CustomButton
            text="Search"
            onClick={async () => {
              setLoading(true);
              setTempData(await getAppointmentsByPhoneNumber(phoneNumber));
              setLoading(false);
            }}
            Icon={SearchIcon}
            color="primary"
          />
        </Stack>

        <CardList
          data={data}
          emptyMessage="No appointments found"
          renderPrimary={(item) => item.name}
          renderSecondary={(item) => (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.25 }}>
              <span>{new Date(item.date).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" })}</span>
              <span>{item.startTime} – {item.endTime}</span>
              <span>with {item.employee}</span>
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mt: 0.5 }}>
                {item.services.map((s: string, i: number) => (
                  <Chip key={i} label={s} size="small" variant="outlined" sx={{ height: 22, fontSize: "0.72rem" }} />
                ))}
              </Box>
            </Box>
          )}
          onEdit={(item) => {
            setSelectedApp(item.actions as unknown as Appointment);
            setIsEditOpen(true);
          }}
          onDelete={(item) => {
            setSelectedApp(item.actions as unknown as Appointment);
            setIsDeleteOpen(true);
          }}
        />

        {isEditOpen && (
          <AppointmentModal
            isOpen={isEditOpen}
            type={"edit"}
            onSubmit={editAppointment}
            onClose={() => setIsEditOpen(false)}
            appointment={selectedApp}
            renderEvents={async () =>
              setTempData(await getAppointmentsByPhoneNumber(phoneNumber))
            }
            allEmployees={employees}
            allServices={services}
          />
        )}
        {isDeleteOpen && (
          <AppointmentModal
            allServices={services}
            type={"delete"}
            onSubmit={deleteAppointment}
            isOpen={isDeleteOpen}
            onClose={() => setIsDeleteOpen(false)}
            appointment={selectedApp}
            renderEvents={async () =>
              setTempData(await getAppointmentsByPhoneNumber(phoneNumber))
            }
            allEmployees={employees}
          />
        )}
      </Paper>
    </Box>
    </AnimatedPage>
  );
}
