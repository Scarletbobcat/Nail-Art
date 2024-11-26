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
import { Service } from "../../types";
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
  const [form, setForm] = useState<Service>({
    id: "0",
    name: "",
  });

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    console.log(await createService(form));
    onClose();
    renderServices();
  };

  return (
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
              <Button type="submit" color="primary" variant="contained">
                Create
              </Button>
            </Box>
          </Box>
        </Stack>
      </Paper>
    </Modal>
  );
}
