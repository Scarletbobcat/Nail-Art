import { useQuery } from "@tanstack/react-query";
import {
  createEmployee,
  deleteEmployee,
  editEmployee,
  getAllEmployees,
} from "../api/employees";
import { Stack, TextField, Button, Fab } from "@mui/material";
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
    return employees.map((row: Employee) => ({
      id: row.id,
      name: row.name,
      color: row.color,
      _raw: row,
    }));
  }, [employees]);

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
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
              sx={{ display: { xs: "none", sm: "inline-flex" } }}
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
            startIcon={<SearchIcon />}
            onClick={refreshEmps}
            sx={{ height: 40, width: { xs: "100%", sm: "auto" }, flexShrink: 0 }}
          >
            Search
          </Button>
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
      <Fab
        color="primary"
        size="medium"
        onClick={() => setIsCreateOpen(true)}
        sx={{
          position: "fixed",
          bottom: 80,
          right: 20,
          zIndex: 10,
          display: { xs: "flex", sm: "none" },
        }}
      >
        <PlusIcon />
      </Fab>
    </Box>
    </AnimatedPage>
  );
}
