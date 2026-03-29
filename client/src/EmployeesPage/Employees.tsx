import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createEmployee,
  deleteEmployee,
  editEmployee,
  getEmployeesPaginated,
} from "../api/employees";
import { Stack, TextField, Button, TablePagination } from "@mui/material";
import PageSkeleton from "../components/PageSkeleton";
import AnimatedPage from "../components/AnimatedPage";
import { useMemo, useState } from "react";
import PlusIcon from "@mui/icons-material/Add";
import { Employee } from "../types";
import { Box, Paper } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import EmployeeModal from "./components/EmployeeModal";
import PageHeader from "../components/PageHeader";
import CardList from "../components/CardList";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function Employees() {
  const queryClient = useQueryClient();
  const [isLoading, setIsLoading] = useState(false);
  const [name, setName] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedEmp, setSelectedEmp] = useState<Employee>({
    id: "",
    name: "",
    color: "",
  });

  const {
    data: employeesData,
    isLoading: employeesLoading,
    error: employeesError,
    refetch,
  } = useQuery({
    queryKey: ["employees", page, rowsPerPage],
    queryFn: () => getEmployeesPaginated(name, page, rowsPerPage),
  });

  const refreshEmps = async () => {
    setIsLoading(true);
    setPage(0);
    await refetch();
    queryClient.invalidateQueries({ queryKey: ["employees"] });
    setIsLoading(false);
  };

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshEmps();
    }
  };

  const totalElements = employeesData?.totalElements ?? 0;

  const data = useMemo(() => {
    if (!employeesData?.content) return [];
    return employeesData.content.map((row: Employee) => ({
      id: row.id,
      name: row.name,
      color: row.color,
      _raw: row,
    }));
  }, [employeesData]);

  if (employeesLoading || isLoading) {
    return <PageSkeleton />;
  }

  if (employeesError) {
    return <div>Error: {employeesError.message}</div>;
  }

  return (
    <AnimatedPage>
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Employees"
          subtitle="Manage your team"
          action={
            <Button
              variant="contained"
              size="small"
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
              sx={{ display: { xs: "none", sm: "inline-flex" }, height: 40, minWidth: 120 }}
            >
              Create
            </Button>
          }
        />
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ mb: 2 }}
          alignItems={{ sm: "center" }}
        >
          <TextField
            fullWidth
            size="small"
            label="Name"
            name="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <Button
            variant="contained"
            size="small"
            startIcon={<SearchIcon />}
            onClick={refreshEmps}
            sx={{ height: 40, display: { xs: "none", sm: "inline-flex" }, flexShrink: 0, minWidth: 120 }}
          >
            Search
          </Button>
          <Stack direction="row" spacing={1.5} sx={{ display: { xs: "flex", sm: "none" }, width: "100%" }}>
            <Button
              variant="outlined"
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
              sx={{ flex: 1, height: 40 }}
            >
              Create
            </Button>
            <Button
              variant="contained"
              startIcon={<SearchIcon />}
              onClick={refreshEmps}
              sx={{ flex: 1, height: 40 }}
            >
              Search
            </Button>
          </Stack>
        </Stack>

        <CardList
          data={data}
          emptyMessage="No employees found"
          renderPrimary={(item) => item.name}
          renderSecondary={(item) => (
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Box
                sx={{
                  width: 14,
                  height: 14,
                  backgroundColor: item.color,
                  borderRadius: "50%",
                  border: "1px solid",
                  borderColor: "divider",
                }}
              />
              <span>{item.color}</span>
            </Stack>
          )}
          onEdit={(item) => {
            setSelectedEmp(item._raw);
            setIsEditOpen(true);
          }}
          onDelete={(item) => {
            setSelectedEmp(item._raw);
            setIsDeleteOpen(true);
          }}
        />
        {totalElements > 0 && (
          <TablePagination
            component="div"
            count={totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
            sx={{ borderTop: "1px solid", borderColor: "divider", mt: 2 }}
          />
        )}
      </Paper>
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
    </Box>
    </AnimatedPage>
  );
}
