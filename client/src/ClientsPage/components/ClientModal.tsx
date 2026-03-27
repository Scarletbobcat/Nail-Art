import {
  Button,
  TextField,
  Stack,
  Box,
  CircularProgress,
} from "@mui/material";
import { useState, FormEvent } from "react";
import { Client, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import ResponsiveModal from "../../components/ResponsiveModal";

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
  const [form, setForm] = useState<Client>({ ...client });
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({ message: "", severity: "error" });

  function changePhoneNumber(inputPhoneNumber: string) {
    const regex = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;
    if (regex.test(inputPhoneNumber)) {
      let newPN = inputPhoneNumber;
      if (
        (newPN.length === 3 && form.phoneNumber?.length === 2) ||
        (newPN.length === 7 && form.phoneNumber?.length === 6)
      ) {
        newPN += "-";
      }
      setForm({ ...form, phoneNumber: newPN });
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
      setAlert({ message: "Failed to create client", severity: "error" });
    }
  };

  return (
    <div>
      <CustomAlert
        {...alert}
        isOpen={isAlertOpen}
        onClose={() => setIsAlertOpen(false)}
      />
      <ResponsiveModal
        open={isOpen}
        onClose={onClose}
        title={`${type.charAt(0).toUpperCase() + type.slice(1)} Client`}
        maxWidth={500}
      >
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
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
                inputProps={{ inputMode: "tel" }}
              />
            </Stack>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, mt: 2 }}>
              <Button onClick={onClose} color="info">
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
          </Stack>
        </Box>
      </ResponsiveModal>
    </div>
  );
}
