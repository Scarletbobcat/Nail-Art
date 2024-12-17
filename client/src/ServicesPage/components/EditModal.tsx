import {
  Modal,
  Button,
  Typography,
  TextField,
  Stack,
  Box,
  Paper,
} from "@mui/material";
import { useState, FormEvent } from "react";
import { Service, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import { editService } from "../../api/services";

export default function EditServiceModal({
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
  const [form, setForm] = useState<Service>(service);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      await editService(form);
      onClose();
      renderServices();
    } catch {
      setIsAlertOpen(true);
      setAlert({
        message: "Failed to edit service",
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
              Edit Service
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
                <Button type="submit" color="primary" variant="contained">
                  Save
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
