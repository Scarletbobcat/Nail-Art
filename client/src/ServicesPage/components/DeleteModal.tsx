import {
  Modal,
  Button,
  Typography,
  TextField,
  Stack,
  Box,
  Paper,
  CircularProgress,
} from "@mui/material";
import { useState, FormEvent } from "react";
import { Service, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import { deleteService } from "../../api/services";

export default function DeleteServiceModal({
  service,
  isOpen,
  onClose,
  renderServices,
}: {
  service: Service;
  isOpen: boolean;
  onClose: () => void;
  renderServices: () => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [form, setForm] = useState<Service>(service);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      console.log(await deleteService(form));
      setIsLoading(false);
      onClose();
      renderServices();
    } catch {
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to delete service",
        severity: "error",
      });
      setIsLoading(false);
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
              Delete Service
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                disabled
                fullWidth
                label="Name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </Stack>
            <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
              <Box>
                <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  color="error"
                  variant="contained"
                  endIcon={isLoading ? <CircularProgress size={20} /> : null}
                  disabled={isLoading}
                >
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
