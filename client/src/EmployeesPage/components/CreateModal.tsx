import {
  Modal,
  Button,
  Typography,
  TextField,
  Stack,
  Box,
  Paper,
} from "@mui/material";
import { PopoverPicker } from "./PopoverPicker";
import { useState, FormEvent } from "react";
import { Employee, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import { createEmployee } from "../../api/employees";

export default function CreateEmployeeModal({
  isOpen,
  onClose,
  renderEmps,
}: {
  isOpen: boolean;
  onClose: () => void;
  renderEmps: () => void;
}) {
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });
  const [form, setForm] = useState<Employee>({
    id: "0",
    name: "",
    color: "#000000",
  });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      await createEmployee(form);
      onClose();
      renderEmps();
    } catch {
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to create employee",
        severity: "error",
      });
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
              Create Employee
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
              <PopoverPicker
                color={form.color ?? ""}
                onChange={(e) => setForm({ ...form, color: e })}
              />
            </Stack>
            <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
              <Box>
                <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
                  Cancel
                </Button>
                <Button type="submit" color="primary" variant="contained">
                  Create
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
