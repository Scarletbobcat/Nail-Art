import { useState, useMemo } from "react";
import { Box, TextField, InputAdornment, IconButton } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import SearchIcon from "@mui/icons-material/Search";
import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../../api/employees";
import { getAppointmentsByPhoneNumber } from "../../api/appointments";
import { Appointment, Employee } from "../../types";
import EditButton from "./components/EditButton";
import DeleteButton from "./components/DeleteButton";
import { Stack, Paper } from "@mui/material";
import EditModal from "../Calendar/components/EditModal";
import DeleteModal from "../Calendar/components/DeleteModal";
import { getAllServices } from "../../api/services";
import CircularLoading from "../../components/CircularLoading";

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
    { field: "id", headerName: "Id", width: 150 },
    { field: "name", headerName: "Name", width: 150 },
    { field: "phoneNumber", headerName: "Phone Number", width: 150 },
    { field: "startTime", headerName: "Start Time", width: 150 },
    { field: "endTime", headerName: "End Time", width: 150 },
    { field: "date", headerName: "Date", width: 150 },
    { field: "employee", headerName: "Employee", width: 150 },
    {
      field: "services",
      headerName: "Services",
      width: 250,
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
          <EditButton
            app={params.row.actions}
            setSelectedApp={setSelectedApp}
            openEdit={() => setIsEditOpen(true)}
          />
          <DeleteButton
            app={params.row.actions}
            setSelectedApp={setSelectedApp}
            openDelete={() => setIsDeleteOpen(true)}
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
    return tempData.map((row) => {
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
        services: row.services.map((s) => {
          return s;
        }),
        actions: row,
      };
    });
  }, [tempData, employees]);

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
      <Paper variant="outlined">
        {/* header */}
        <TextField
          label="Phone Number"
          sx={{ m: 1, width: "25ch" }}
          onChange={(e) => changePhoneNumber(e.target.value)}
          onKeyDown={handleKeyDown}
          value={phoneNumber}
          slotProps={{
            input: {
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
            },
          }}
        />
        {/* content */}
        <Box sx={{ height: 400, width: "100%", m: 1 }}>
          <DataGrid
            rows={data}
            columns={columns}
            initialState={{
              pagination: {
                paginationModel: {
                  pageSize: 10,
                },
              },
            }}
            pageSizeOptions={[10]}
            disableRowSelectionOnClick
          />
        </Box>
        {isEditOpen && (
          <EditModal
            isOpen={isEditOpen}
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
          <DeleteModal
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
    </>
  );
}
