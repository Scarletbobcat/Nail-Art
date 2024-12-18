import { Button } from "@mui/material";
import { remindAppointments } from "../../../api/appointments";
import { useState } from "react";

export default function ReminderButton() {
  const [isLoading, setIsLoading] = useState(false);
  return (
    <Button
      variant="contained"
      color="primary"
      onClick={async () => {
        try {
          setIsLoading(true);
          await remindAppointments();
          setIsLoading(false);
        } catch (error) {
          console.error(error);
          setIsLoading(false);
        }
      }}
      disabled={isLoading}
    >
      Remind Appointments
    </Button>
  );
}
