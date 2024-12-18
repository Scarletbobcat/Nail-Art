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
import Select from "@mui/material/Select";
import Chip from "@mui/material/Chip";
import { DateTimePicker, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import { FormEvent } from "react";
import { Appointment, Employee, Service } from "../../../types";
import { deleteAppointment } from "../../../api/appointments";
import { useState } from "react";
import CustomAlert from "../../../components/Alert";
import { Alert } from "../../../types";

interface AppointmentDeleteModalProps {
  appointment: Appointment;
  onClose: () => void;
  isOpen: boolean;
  renderEvents: () => void;
  allEmployees: Employee[];
  services: Service[];
}

export default function AppointmentDeleteModal({
  appointment,
  onClose,
  renderEvents,
  isOpen,
  allEmployees,
  services,
}: AppointmentDeleteModalProps) {
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  const handleDelete = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // delete appointment
    try {
      await deleteAppointment(appointment);
      // closing modal and re-rendering events
      onClose();
      renderEvents();
    } catch {
      // show alert if failed to create appointment
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to delete appointment",
        severity: "error",
      });
    }
  };

  return (
    <div>
      <CustomAlert
        isOpen={isAlertOpen}
        message={alert.message}
        severity={alert.severity}
        onClose={() => setIsAlertOpen(false)}
      />
      <Modal
        open={isOpen}
        onClose={onClose}
        aria-labelledby="modal-title"
        aria-describedby="modal-description"
      >
        <Paper
          component="form"
          onSubmit={handleDelete}
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
              Delete Appointment
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                fullWidth
                disabled
                label="Name"
                value={appointment.name}
                variant="outlined"
              />
              <TextField
                fullWidth
                disabled
                label="Phone Number"
                value={appointment.phoneNumber}
                variant="outlined"
              />
            </Stack>
            <Stack spacing={2}>
              <Stack direction="row" spacing={2}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    label="Start"
                    disabled
                    value={
                      appointment
                        ? dayjs(appointment.date + appointment.startTime)
                        : dayjs()
                    }
                  />
                </LocalizationProvider>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    value={
                      appointment
                        ? dayjs(appointment.date + appointment.endTime)
                        : dayjs()
                    }
                    label="End"
                    disabled
                  />
                </LocalizationProvider>
              </Stack>
              <Stack direction="row" spacing={2}>
                <FormControl fullWidth>
                  <InputLabel id="employee-label">Employee</InputLabel>
                  <Select
                    labelId="employee-label"
                    fullWidth
                    disabled
                    label="Employee"
                    value={appointment.employeeId}
                    variant="outlined"
                  >
                    {allEmployees.map((employee) =>
                      employee.id === appointment.employeeId ? (
                        <MenuItem key={employee.id} value={employee.id}>
                          {employee.name}
                        </MenuItem>
                      ) : null
                    )}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="service-label">Services</InputLabel>
                  <Select
                    disabled
                    labelId="service-label"
                    multiple
                    fullWidth
                    label="Service"
                    value={appointment.services}
                    renderValue={(selected) => (
                      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                        {selected.map((serviceId) => {
                          const serviceName =
                            services.find((service) => service.id === serviceId)
                              ?.name || serviceId;
                          return <Chip key={serviceId} label={serviceName} />;
                        })}
                      </Box>
                    )}
                    variant="outlined"
                  ></Select>
                </FormControl>
              </Stack>
            </Stack>
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
        </Paper>
      </Modal>
    </div>
  );
}
