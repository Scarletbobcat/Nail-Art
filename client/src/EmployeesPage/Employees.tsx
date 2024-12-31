import { useQuery } from "@tanstack/react-query";
import {
  createEmployee,
  deleteEmployee,
  editEmployee,
  getAllEmployees,
} from "../api/employees";
import { Stack, TextField } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import PlusIcon from "@mui/icons-material/Add";
import { Employee } from "../types";
import { Typography, Box, Paper } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import SearchIcon from "@mui/icons-material/Search";
import EmployeeModal from "./components/EmployeeModal";
import CustomButton from "../components/Button";

export default function Employees() {
  const theme = useTheme();
  const [isLoading, setIsLoading] = useState(false);
  const [name, setName] = useState("");
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedEmp, setSelectedEmp] = useState<Employee>({
    id: "",
    name: "",
    color: "",
  });

  const {
    data: employees,
    isLoading: employeesLoading,
    error: employeesError,
    refetch,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: () => getAllEmployees(name),
  });

  const columns = [
    { field: "id", headerName: "ID", flex: 1 },
    { field: "name", headerName: "Name", flex: 1 },
    {
      field: "color",
      headerName: "Color",
      flex: 1,
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{
            display: "flex",
            justifyContent: "left",
            alignItems: "center",
            width: "100%",
            height: "100%",
          }}
        >
          <Stack direction="row" alignItems="center" spacing={1}>
            <Box
              sx={{
                width: 24,
                height: 24,
                backgroundColor: params.value,
                border: "1px solid #000",
                borderRadius: "50%",
              }}
            />
            <Typography>{params.value}</Typography>
          </Stack>
        </Box>
      ),
    },
    {
      field: "Actions",
      headerName: "Actions",
      flex: 1,
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{
            display: "flex",
            justifyContent: "left",
            alignItems: "center",
            width: "100%",
            height: "100%",
          }}
        >
          <Stack direction="row" spacing={2}>
            <CustomButton
              Icon={EditIcon}
              color="primary"
              onClick={() => {
                setSelectedEmp(params.row.actions);
                setIsEditOpen(true);
              }}
            />
            <CustomButton
              color="error"
              Icon={DeleteIcon}
              onClick={() => {
                setSelectedEmp(params.row.actions);
                setIsDeleteOpen(true);
              }}
            />
          </Stack>
        </Box>
      ),
    },
  ];

  const refreshEmps = async () => {
    setIsLoading(true);
    await refetch();
    setIsLoading(false);
  };

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshEmps();
    }
  };

  const data = useMemo(() => {
    if (!employees) return [];
    return employees.map((row: Employee) => {
      return {
        id: row.id,
        name: row.name,
        color: row.color,
        actions: row,
      };
    });
  }, [employees]);

  if (employeesLoading || isLoading) {
    return <CircularLoading />;
  }

  if (employeesError) {
    return <div>Error: {employeesError.message}</div>;
  }

  return (
    <Box
      sx={{
        padding: 4,
        height: "calc(100vh)",
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
          <Stack
            direction="row"
            sx={{
              backgroundColor: theme.palette.primary.main,
              padding: 2,
              borderRadius: 2,
            }}
            justifyContent="space-between"
          >
            <Typography variant="h4" sx={{ color: "white" }}>
              Employees
            </Typography>
            <CustomButton
              text={"Create"}
              color="primary"
              sx={{
                color: "white",
              }}
              onClick={() => setIsCreateOpen(true)}
              Icon={PlusIcon}
            />
          </Stack>
          <Stack direction="row" spacing={2} justifyContent="space-between">
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                name="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </Stack>
            <CustomButton
              text="Search"
              onClick={refreshEmps}
              Icon={SearchIcon}
              color="primary"
            />
          </Stack>
          <DataGrid
            rows={data}
            columns={columns}
            initialState={{
              pagination: {
                pageSize: 10,
              },
            }}
            rowsPerPageOptions={[10]}
            disableSelectionOnClick
          />
        </Stack>
        {isEditOpen && (
          <EmployeeModal
            employee={selectedEmp}
            onSubmit={editEmployee}
            type={"edit"}
            isOpen={isEditOpen}
            onClose={() => setIsEditOpen(false)}
            renderEmps={refreshEmps}
          />
        )}
        {isDeleteOpen && (
          <EmployeeModal
            employee={selectedEmp}
            onSubmit={deleteEmployee}
            type={"delete"}
            isOpen={isDeleteOpen}
            onClose={() => setIsDeleteOpen(false)}
            renderEmps={refreshEmps}
          />
        )}
        {isCreateOpen && (
          <EmployeeModal
            employee={{ id: "", name: "", color: "#000000" }}
            onSubmit={createEmployee}
            type={"create"}
            renderEmps={refreshEmps}
            isOpen={isCreateOpen}
            onClose={() => setIsCreateOpen(false)}
          />
        )}
      </Paper>
    </Box>
  );
}
