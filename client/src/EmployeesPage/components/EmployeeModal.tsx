import {
  Modal,
  Button,
  Typography,
  TextField,
  Stack,
  Box,
  Paper,
  CircularProgress,
} from "@mui/material";
import { PopoverPicker } from "./PopoverPicker";
import { useState, FormEvent } from "react";
import { Employee, Alert } from "../../types";
import CustomAlert from "../../components/Alert";

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
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });
  const [form, setForm] = useState<Employee>({
    ...employee,
  });

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
      setAlert({
        message: "Failed to create employee",
        severity: "error",
      });
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
      <Modal open={isOpen} onClose={onClose}>
        <Paper
          component="form"
          onSubmit={handleSubmit}
          sx={{
            position: "absolute",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            width: { xs: "90%", sm: 330 },
            bgcolor: "background.paper",
            boxShadow: 24,
            p: 4,
          }}
        >
          <Stack spacing={2}>
            <Typography
              id="modal-title"
              variant="h5"
              component="h6"
              sx={{ mb: 4, fontWeight: "bold" }}
            >
              {type.charAt(0).toUpperCase() + type.slice(1)} Employee
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
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
            <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
              <Box>
                <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
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
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
