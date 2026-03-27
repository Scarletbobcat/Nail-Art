import { Stack, Chip } from "@mui/material";
import { Employee } from "../../../types";

export default function EmployeeSelector({
  employees,
  selectedId,
  onSelect,
}: {
  employees: Employee[];
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  return (
    <Stack
      direction="row"
      spacing={1}
      sx={{
        overflowX: "auto",
        pb: 1,
        "&::-webkit-scrollbar": { display: "none" },
        scrollbarWidth: "none",
      }}
    >
      {employees.map((emp) => (
        <Chip
          key={emp.id}
          label={emp.name}
          onClick={() => onSelect(emp.id)}
          variant={emp.id === selectedId ? "filled" : "outlined"}
          color={emp.id === selectedId ? "primary" : "default"}
          sx={{
            flexShrink: 0,
            fontWeight: emp.id === selectedId ? 600 : 400,
            px: 1,
          }}
        />
      ))}
    </Stack>
  );
}
