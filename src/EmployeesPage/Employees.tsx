import { useQuery } from "@tanstack/react-query";
import { getAllEmployees } from "../api/employees";
import { Stack } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";

export default function Employees() {
  const {
    data: employees,
    isLoading: employeesLoading,
    error: employeesError,
  } = useQuery({
    queryKey: ["employees"],
    queryFn: getAllEmployees,
  });

  if (employeesLoading) {
    return <CircularLoading />;
  }

  if (employeesError) {
    return <div>Error: {employeesError.message}</div>;
  }

  const columns = [
    { field: "id", headerName: "ID", flex: 1 },
    { field: "name", headerName: "Name", flex: 1 },
    { field: "color", headerName: "Color", flex: 1 },
  ];

  return (
    <Stack>
      <DataGrid
        rows={employees}
        columns={columns}
        // pageSize={10}
        // checkboxSelection
        disableRowSelectionOnClick
      />
    </Stack>
  );
}
