import {
  Button,
  TextField,
  Stack,
  Box,
  CircularProgress,
} from "@mui/material";
import { useState, FormEvent } from "react";
import { Service, Alert } from "../../types";
import CustomAlert from "../../components/Alert";
import ResponsiveModal from "../../components/ResponsiveModal";

export default function ServiceModal({
  service,
  type,
  onSubmit,
  isOpen,
  onClose,
  renderServices,
}: {
  service: Service;
  type: "create" | "edit" | "delete";
  onSubmit: (e: Service) => void;
  isOpen: boolean;
  onClose: () => void;
  renderServices: () => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [form, setForm] = useState<Service>({ ...service });
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({ message: "", severity: "error" });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      await onSubmit(form);
      setIsLoading(false);
      onClose();
      renderServices();
    } catch {
      setIsAlertOpen(true);
      setAlert({ message: "Failed to create service", severity: "error" });
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
        title={`${type.charAt(0).toUpperCase() + type.slice(1)} Service`}
        maxWidth={400}
      >
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            <TextField
              label="Name"
              fullWidth
              disabled={type === "delete"}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
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
