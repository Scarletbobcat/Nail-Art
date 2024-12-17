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
import { deleteEmployee } from "../../api/employees";

export default function DeleteEmployeeModal({
  emp,
  isOpen,
  onClose,
  renderEmps,
}: {
  emp: Employee;
  isOpen: boolean;
  onClose: () => void;
  renderEmps: () => void;
}) {
  const [form, setForm] = useState<Employee>(emp);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      console.log(await deleteEmployee(form));
      onClose();
      renderEmps();
    } catch {
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to delete employee",
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
              Delete Employee
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                disabled
                label="Name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
              <PopoverPicker
                disabled
                color={form.color ?? ""}
                onChange={(e) => setForm({ ...form, color: e })}
              />
            </Stack>
            <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
              <Box>
                <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
                  Cancel
                </Button>
                <Button type="submit" color="error" variant="contained">
                  Delete
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
