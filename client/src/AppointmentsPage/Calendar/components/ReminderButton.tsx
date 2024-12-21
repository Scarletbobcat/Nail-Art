import { Button, Box } from "@mui/material";
import { remindAppointments } from "../../../api/appointments";
import { useState } from "react";
import CustomAlert from "../../../components/Alert";

interface AxiosError extends Error {
  response?: {
    data?: string;
  };
}

export default function ReminderButton() {
  const [isLoading, setIsLoading] = useState(false);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<{
    message: string;
    severity: "error" | "success" | "info" | "warning";
  }>({
    message: "",
    severity: "error",
  });
  return (
    <Box>
      <CustomAlert
        {...alert}
        isOpen={isAlertOpen}
        onClose={() => setIsAlertOpen(false)}
      />
      <Button
        variant="contained"
        color="primary"
        onClick={async () => {
          try {
            setIsLoading(true);
            await remindAppointments();
            setIsLoading(false);
          } catch (error) {
            // console.error(error);
            setIsAlertOpen(true);
            const axiosError = error as AxiosError;
            const message =
              axiosError.response?.data ||
              axiosError.message ||
              "Unknown error";
            setAlert({
              message: message,
              severity: "error",
            });
            setIsLoading(false);
          }
        }}
        disabled={isLoading}
        fullWidth
      >
        Remind Appointments
      </Button>
    </Box>
  );
}
