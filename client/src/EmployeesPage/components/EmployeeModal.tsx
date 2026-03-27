import {
  Button,
  TextField,
  Stack,
  Box,
  CircularProgress,
} from "@mui/material";
import { PopoverPicker } from "./PopoverPicker";
import { useState, FormEvent } from "react";
import { Employee, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import ResponsiveModal from "../../components/ResponsiveModal";

export default function EmployeeModal({
  employee,
  isOpen,
  onClose,
  renderEmps,
  type,
  onSubmit,
}: {
  employee: Employee;
  isOpen: boolean;
  onClose: () => void;
  renderEmps: () => void;
  type: "create" | "edit" | "delete";
  onSubmit: (e: Employee) => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({ message: "", severity: "error" });
  const [form, setForm] = useState<Employee>({ ...employee });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      await onSubmit(form);
      setIsLoading(false);
      onClose();
      renderEmps();
    } catch {
      setIsAlertOpen(true);
      setAlert({ message: "Failed to create employee", severity: "error" });
      setIsLoading(false);
    }
  };

  return (
    <div>
      <CustomAlert
        {...alert}
        isOpen={isAlertOpen}
        onClose={() => setIsAlertOpen(false)}
      />
      <ResponsiveModal
        open={isOpen}
        onClose={onClose}
        title={`${type.charAt(0).toUpperCase() + type.slice(1)} Employee`}
        maxWidth={400}
      >
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Name"
                fullWidth
                disabled={type === "delete"}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
              <PopoverPicker
                disabled={type === "delete"}
                color={form.color ?? ""}
                onChange={(e) => setForm({ ...form, color: e })}
              />
            </Stack>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, mt: 2 }}>
              <Button onClick={onClose} color="info">
                Cancel
              </Button>
              <Button
                type="submit"
                color={type === "delete" ? "error" : "primary"}
                variant="contained"
                endIcon={isLoading ? <CircularProgress size={20} /> : null}
                disabled={isLoading}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </Button>
            </Box>
          </Stack>
        </Box>
      </ResponsiveModal>
    </div>
  );
}
