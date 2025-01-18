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
import { Client, Alert } from "../../types";
import CustomAlert from "../../components/Alert";

export default function ClientModal({
  client,
  type,
  onSubmit,
  isOpen,
  onClose,
  renderEntities,
}: {
  client: Client;
  type: "create" | "edit" | "delete";
  onSubmit: (e: Client) => void;
  isOpen: boolean;
  onClose: () => void;
  renderEntities: () => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [form, setForm] = useState<Client>({
    ...client,
  });
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });

  function changePhoneNumber(inputPhoneNumber: string) {
    const regex = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;
    if (regex.test(inputPhoneNumber)) {
      let newPN = inputPhoneNumber;
      // conditionally adds hyphen only when adding to phone number, not deleting
      if (
        (newPN.length === 3 && form.phoneNumber?.length === 2) ||
        (newPN.length === 7 && form.phoneNumber?.length === 6)
      ) {
        newPN += "-";
      }
      setForm({ ...form, phoneNumber: newPN });
    } else {
      // console.error("Phone number does not match regex");
    }
  }

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      await onSubmit(form);
      setIsLoading(false);
      onClose();
      renderEntities();
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
            width: { xs: "90%", sm: 330, md: 500 },
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
              {type.charAt(0).toUpperCase() + type.slice(1)} Client
            </Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                name="name"
                fullWidth
                disabled={type === "delete"}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
              <TextField
                label="Phone Number"
                fullWidth
                value={form.phoneNumber}
                disabled={type === "delete"}
                onChange={(e) => changePhoneNumber(e.target.value)}
                name="phoneNumber"
              />
            </Stack>
            <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
              <Box>
                <Button onClick={onClose} color="info" sx={{ mr: 2 }}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  color={type === "delete" ? "error" : "primary"}
                  variant="contained"
                  endIcon={isLoading ? <CircularProgress size={20} /> : null}
                  disabled={isLoading}
                >
                  {type.charAt(0).toUpperCase() + type.slice(1)}
                </Button>
              </Box>
            </Box>
          </Stack>
        </Paper>
      </Modal>
    </div>
  );
}
