import {
  Modal,
  Button,
  Box,
  Typography,
  TextField,
  Grid2,
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
import { Appointment } from "../../../types/AppointmentI";
import { editAppointment } from "../../../api/appointments";

interface Service {
  id: string;
  name: string;
}

interface Employee {
  id: string;
  name: string;
  color?: string;
}

interface AppointmentEditModalProps {
  appointment: Appointment;
  onClose: () => void;
  isOpen: boolean;
  renderEvents: () => void;
  allServices: Service[];
  allEmployees: Employee[];
}

export default function AppointmentEditModal({
  appointment,
  onClose,
  renderEvents,
  isOpen,
  allServices,
  allEmployees,
}: AppointmentEditModalProps) {
  const [form, setForm] = useState(appointment);

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // save the appointment
    await editAppointment(form);
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
          <Box>
            <Typography
              id="modal-title"
              variant="h5"
              component="h6"
              sx={{ mb: 4, fontWeight: "bold" }}
            >
              Edit/Delete Appointment
            </Typography>
            <Grid2 container alignItems="center" spacing={2} sx={{ mb: 2 }}>
              <Grid2 size={6}>
                <TextField
                  fullWidth
                  label="Name"
                  value={form.name}
                  variant="outlined"
                  onChange={(e) => {
                    setForm({ ...form, name: e.target.value });
                  }}
                />
              </Grid2>
              <Grid2 size={6}>
                <TextField
                  fullWidth
                  label="Phone Number"
                  value={form.phoneNumber}
                  variant="outlined"
                  onChange={(e) => {
                    setForm({ ...form, phoneNumber: e.target.value });
                  }}
                />
              </Grid2>
            </Grid2>
            <Grid2 container alignItems="center" spacing={2} sx={{ mb: 2 }}>
              <Grid2 size={6}>
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
              </Grid2>
              <Grid2 size={6}>
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
              </Grid2>
              <Grid2 size={6}>
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
              </Grid2>
              <Grid2 size={6}>
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
              </Grid2>
            </Grid2>
          </Box>
          <Box sx={{ mt: 3, display: "flex", justifyContent: "space-between" }}>
            <Button onClick={onClose} color="error" variant="contained">
              Delete
            </Button>
            <Box>
              <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
                Cancel
              </Button>
              <Button type="submit" color="primary" variant="contained">
                Save
              </Button>
            </Box>
          </Box>
        </Paper>
      </Modal>
    </div>
  );
}
