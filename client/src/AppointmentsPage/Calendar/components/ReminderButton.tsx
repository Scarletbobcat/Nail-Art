import { Button } from "@mui/material";
import { remindAppointments } from "../../../api/appointments";

export default function ReminderButton() {
  return (
    <Button
      variant="contained"
      color="primary"
      onClick={() => {
        try {
          remindAppointments();
        } catch (error) {
          console.error(error);
        }
      }}
    >
      Remind Appointments
    </Button>
  );
}
