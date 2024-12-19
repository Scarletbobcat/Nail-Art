import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../api/employees";
import { IconButton, InputAdornment, Stack, TextField } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import EditButton from "./components/EditButton";
import { Employee } from "../types";
import DeleteButton from "./components/DeleteButton";
import EditEmployeeModal from "./components/EditModal";
import { Typography, Box, Paper } from "@mui/material";
import DeleteEmployeeModal from "./components/DeleteModal";
import { useTheme } from "@mui/material/styles";
import SearchIcon from "@mui/icons-material/Search";
import CreateButton from "./components/CreateButton";
import CreateModal from "./components/CreateModal";

export default function Employees() {
  const theme = useTheme();
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
    refetch: refreshEmps,
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
            <EditButton
              emp={params.row.actions}
              setSelectedEmp={setSelectedEmp}
              openEdit={() => setIsEditOpen(true)}
            />
            <DeleteButton
              emp={params.row.actions}
              setSelectedEmp={setSelectedEmp}
              openDelete={() => setIsDeleteOpen(true)}
            />
          </Stack>
        </Box>
      ),
    },
  ];

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

  if (employeesLoading) {
    return <CircularLoading />;
  }

  if (employeesError) {
    return <div>Error: {employeesError.message}</div>;
  }

  return (
    <Box
      sx={{
        padding: 4,
      }}
    >
      <Paper
        variant="outlined"
        sx={{
          padding: 3,
        }}
      >
        <Stack spacing={2}>
          <Stack
            direction="row"
            sx={{
              backgroundColor: theme.palette.primary.main,
              padding: 2,
              borderRadius: 2,
            }}
          >
            <Typography variant="h4" sx={{ color: "white" }}>
              Employees
            </Typography>
          </Stack>
          <Stack direction="row" spacing={2} justifyContent="space-between">
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                onChange={(e) => setName(e.target.value)}
                onKeyDown={handleKeyDown}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={async () => {
                            refreshEmps();
                          }}
                        >
                          <SearchIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
              />
            </Stack>
            <CreateButton openCreate={() => setIsCreateOpen(true)} />
          </Stack>
          <DataGrid
            rows={data}
            columns={columns}
            disableRowSelectionOnClick
            initialState={{
              pagination: {
                paginationModel: {
                  pageSize: 10,
                },
              },
            }}
            pageSizeOptions={[10]}
          />
        </Stack>
        {isEditOpen && (
          <EditEmployeeModal
            emp={selectedEmp}
            isOpen={isEditOpen}
            onClose={() => setIsEditOpen(false)}
            renderEmps={refreshEmps}
          />
        )}
        {isDeleteOpen && (
          <DeleteEmployeeModal
            emp={selectedEmp}
            isOpen={isDeleteOpen}
            onClose={() => setIsDeleteOpen(false)}
            renderEmps={refreshEmps}
          />
        )}
        {isCreateOpen && (
          <CreateModal
            renderEmps={refreshEmps}
            isOpen={isCreateOpen}
            onClose={() => setIsCreateOpen(false)}
          />
        )}
      </Paper>
    </Box>
  );
}
