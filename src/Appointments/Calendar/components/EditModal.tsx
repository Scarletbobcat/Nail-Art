import {
  Modal,
  Button,
  Box,
  Typography,
  TextField,
  Grid2,
  Paper,
} from "@mui/material";
import { DateTimePicker, LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";

interface Appointment {
  id: string;
  employeeId: string;
  date: string;
  startTime: string;
  endTime: string;
  name: string;
  services: string[];
  phoneNumber: string;
}

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
  appointment: Appointment | undefined;
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
          // onSubmit={handleSave}
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
                  value={appointment ? appointment.name : ""}
                  variant="outlined"
                />
              </Grid2>
              <Grid2 size={6}>
                <TextField
                  fullWidth
                  label="Phone Number"
                  value={appointment ? appointment.phoneNumber : ""}
                  variant="outlined"
                />
              </Grid2>
            </Grid2>
            <Grid2 container alignItems="center" spacing={2} sx={{ mb: 2 }}>
              <Grid2 size={6}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    label="Start"
                    value={
                      appointment
                        ? dayjs(appointment.date + appointment.startTime)
                        : dayjs()
                    }
                  />
                </LocalizationProvider>
              </Grid2>
              <Grid2 size={6}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DateTimePicker
                    value={
                      appointment
                        ? dayjs(appointment.date + appointment.endTime)
                        : dayjs()
                    }
                    label="End"
                  />
                </LocalizationProvider>
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
