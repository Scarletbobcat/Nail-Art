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
import { createService } from "../../api/services";

export default function CreateServiceModal({
  isOpen,
  onClose,
  renderServices,
}: {
  isOpen: boolean;
  onClose: () => void;
  renderServices: () => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [form, setForm] = useState<Service>({
    id: 0,
    name: "",
  });
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      console.log(await createService(form));
      setIsLoading(false);
      onClose();
      renderServices();
    } catch {
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to create service",
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
              Create Service
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                fullWidth
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
                  color="primary"
                  variant="contained"
                  endIcon={isLoading ? <CircularProgress size={20} /> : null}
                  disabled={isLoading}
                >
                  Create
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
