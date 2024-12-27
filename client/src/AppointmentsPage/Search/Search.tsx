import { useState, useMemo } from "react";
import {
  Box,
  TextField,
  InputAdornment,
  IconButton,
  Typography,
} from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import SearchIcon from "@mui/icons-material/Search";
import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../../api/employees";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import {
  deleteAppointment,
  editAppointment,
  getAppointmentsByPhoneNumber,
} from "../../api/appointments";
import { Appointment, Employee, Service } from "../../types";
import { Stack, Paper } from "@mui/material";
import { getAllServices } from "../../api/services";
import CircularLoading from "../../components/CircularLoading";
import { useTheme } from "@mui/material/styles";
import AppointmentModal from "../components/AppointmentModal";
import CustomButton from "../../components/Button";

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
  const theme = useTheme();
  const [phoneNumber, setPhoneNumber] = useState("");
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

  function changePhoneNumber(inputPhoneNumber: string) {
    const regex = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;
    if (regex.test(inputPhoneNumber)) {
      let newPN = inputPhoneNumber;
      // conditionally adds hyphen only when adding to phone number, not deleting
      if (
        (newPN.length === 3 && phoneNumber.length === 2) ||
        (newPN.length === 7 && phoneNumber.length === 6)
      ) {
        newPN += "-";
      }
      setPhoneNumber(newPN);
    } else {
      console.error("Phone number does not match regex");
    }
  }

  // header of table
  const columns = [
    { field: "id", headerName: "Id", width: 50 },
    { field: "name", headerName: "Name", flex: 1 },
    { field: "phoneNumber", headerName: "Phone Number", flex: 1 },
    { field: "startTime", headerName: "Start Time", flex: 1 },
    { field: "endTime", headerName: "End Time", flex: 1 },
    { field: "date", headerName: "Date", flex: 1 },
    { field: "employee", headerName: "Employee", flex: 1 },
    {
      field: "services",
      headerName: "Services",
      flex: 1,
      // this is what is rendered in the cell if wanted to change in the future
      // (maybe change so multiple services is easier to read)
      // renderCell: (s) => {
      //   return s.value;
      // },
    },
    {
      field: "Actions",
      headerName: "Actions",
      flex: 1,
      renderCell: (params: GridRenderCellParams) => (
        <Stack direction="row" spacing={1}>
          <CustomButton
            color="primary"
            Icon={EditIcon}
            onClick={() => {
              setSelectedApp(params.row.actions);
              setIsEditOpen(true);
            }}
          />
          <CustomButton
            color="error"
            Icon={DeleteIcon}
            onClick={() => {
              setSelectedApp(params.row.actions);
              setIsDeleteOpen(true);
            }}
          />
        </Stack>
      ),
    },
  ];

  // searches if enter key is pressed
  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      setLoading(true);
      setTempData(await getAppointmentsByPhoneNumber(phoneNumber));
      setLoading(false);
    }
  };

  // this makes the appointment data being fetched match the table format
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
          "en-US"
        ),
        endTime: new Date(row.date + row.endTime).toLocaleTimeString("en-US"),
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
    return <CircularLoading />;
  }

  if (employeesError) {
    return <div>Error fetching employees</div>;
  }
  if (servicesError) {
    return <div>Error fetching services</div>;
  }

  return (
    <>
      <Box
        sx={{
          padding: 4,
          height: "100vh",
        }}
      >
        <Paper
          variant="outlined"
          sx={{
            padding: 3,
            height: "100%",
          }}
        >
          <Stack spacing={2} sx={{ height: "100%" }}>
            {/* header */}
            <Stack
              direction="row"
              sx={{
                justifyContent: "space-between",
                backgroundColor: theme.palette.primary.main,
                padding: 2,
                borderRadius: 2,
              }}
            >
              <Typography variant="h4" sx={{ color: "white" }}>
                Appointments
              </Typography>
            </Stack>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Phone Number"
                onChange={(e) => changePhoneNumber(e.target.value)}
                onKeyDown={handleKeyDown}
                value={phoneNumber}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={async () => {
                          setLoading(true);
                          setTempData(
                            await getAppointmentsByPhoneNumber(phoneNumber)
                          );
                          setLoading(false);
                        }}
                      >
                        <SearchIcon />
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />
            </Stack>
            {/* content */}
            <Box sx={{ height: 400, width: "100%", m: 1 }}>
              <DataGrid
                rows={data}
                columns={columns}
                initialState={{
                  pagination: {
                    pageSize: 10,
                  },
                }}
                disableSelectionOnClick
              />
            </Box>
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
          </Stack>
        </Paper>
      </Box>
    </>
  );
}
