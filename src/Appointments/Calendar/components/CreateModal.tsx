import {
  Modal,
  Button,
  Box,
  Typography,
  TextField,
  Stack,
  Paper,
  MenuItem,
  InputLabel,
  FormControl,
} from "@mui/material";
import Select, { SelectChangeEvent } from "@mui/material/Select";
import Chip from "@mui/material/Chip";
import { DateTimePicker, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import { useState, FormEvent } from "react";
import { Appointment, Employee, Service } from "../../../types";
import { createAppointment } from "../../../api/appointments";

interface AppointmentCreateModalProps {
  appointment: Appointment;
  onClose: () => void;
  isOpen: boolean;
  renderEvents: () => void;
  allServices: Service[];
  allEmployees: Employee[];
}

export default function AppointmentCreateModal({
  appointment,
  onClose,
  renderEvents,
  isOpen,
  allServices,
  allEmployees,
}: AppointmentCreateModalProps) {
  const [form, setForm] = useState(appointment);

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // save the appointment
    await createAppointment(form);
    // closing modal and re-rendering events
    onClose();
    renderEvents();
  };

  return (
    <div>
      <Modal
        open={isOpen}
        onClose={onClose}
        aria-labelledby="modal-title"
        aria-describedby="modal-description"
      >
        <Paper
          component="form"
          onSubmit={handleSave}
          sx={{
            position: "absolute",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            width: { xs: "90%", sm: 545 },
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
              Create Appointment
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                fullWidth
                label="Name"
                value={form.name}
                variant="outlined"
                onChange={(e) => {
                  setForm({ ...form, name: e.target.value });
                }}
              />
              <TextField
                fullWidth
                label="Phone Number"
                value={form.phoneNumber}
                variant="outlined"
                onChange={(e) => {
                  setForm({ ...form, phoneNumber: e.target.value });
                }}
              />
            </Stack>
            <Stack spacing={2}>
              <Stack direction="row" spacing={2}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    label="Start"
                    value={form ? dayjs(form.date + form.startTime) : dayjs()}
                    onChange={(date) => {
                      setForm({
                        ...form,
                        date: date ? date.format("YYYY-MM-DD") : form.date,
                        startTime: date
                          ? "T" + date.format("HH:mm:ss")
                          : form.startTime,
                      });
                    }}
                  />
                </LocalizationProvider>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    value={form ? dayjs(form.date + form.endTime) : dayjs()}
                    label="End"
                    onChange={(date) => {
                      setForm({
                        ...form,
                        date: date ? date.format("YYYY-MM-DD") : form.date,
                        endTime: date
                          ? "T" + date.format("HH:mm:ss")
                          : form.endTime,
                      });
                    }}
                  />
                </LocalizationProvider>
              </Stack>
              <Stack direction="row" spacing={2}>
                <FormControl fullWidth>
                  <InputLabel id="employee-label">Employee</InputLabel>
                  <Select
                    labelId="employee-label"
                    fullWidth
                    label="Employee"
                    value={form.employeeId}
                    onChange={(e: SelectChangeEvent) => {
                      setForm({ ...form, employeeId: e.target.value });
                    }}
                    variant="outlined"
                  >
                    {allEmployees.map((employee) => (
                      <MenuItem key={employee.id} value={employee.id}>
                        {employee.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="service-label">Services</InputLabel>
                  <Select
                    labelId="service-label"
                    multiple
                    fullWidth
                    label="Service"
                    value={form.services}
                    onChange={(e: SelectChangeEvent<string[]>) => {
                      setForm({
                        ...form,
                        services:
                          typeof e.target.value === "string"
                            ? e.target.value.split(",")
                            : e.target.value,
                      });
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} />
                        ))}
                      </Box>
                    )}
                    variant="outlined"
                  >
                    {allServices.map((service) => (
                      <MenuItem key={service.id} value={service.name}>
                        {service.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Stack>
            </Stack>
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
        </Paper>
      </Modal>
    </div>
  );
}
