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
import { Employee } from "../../types";
import { editEmployee } from "../../api/employees";

export default function EditEmployeeModal({
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

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    await editEmployee(form);
    onClose();
    renderEmps();
  };

  return (
    <Modal open={isOpen} onClose={onClose}>
      <Paper
        component="form"
        onSubmit={handleSave}
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
            Edit Employee
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
                Save
              </Button>
            </Box>
          </Box>
        </Stack>
      </Paper>
    </Modal>
  );
}
