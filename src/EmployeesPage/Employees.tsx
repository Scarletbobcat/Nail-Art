import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../api/employees";
import { Stack } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import EditButton from "./components/EditButton";
import { Employee } from "../types";
import DeleteButton from "./components/DeleteButton";
import EditEmployeeModal from "./components/EditModal";
import { Typography, Box } from "@mui/material";
import DeleteEmployeeModal from "./components/DeleteModal";

export default function Employees() {
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
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
    queryFn: getAllEmployees,
  });

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

  return (
    <div>
      <Stack>
        <DataGrid rows={data} columns={columns} disableRowSelectionOnClick />
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
    </div>
  );
}
