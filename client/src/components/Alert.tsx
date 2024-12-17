import { Alert, Box } from "@mui/material";
import { SxProps, Theme } from "@mui/system";

export default function CustomAlert({
  message,
  severity,
  sx,
  isOpen,
  onClose,
}: {
  message: string;
  severity: "error" | "warning" | "info" | "success";
  sx?: SxProps<Theme>;
  isOpen: boolean;
  onClose: () => void;
}) {
  if (!isOpen) {
    return null;
  }
  return (
    <Box
      sx={{
        ...sx,
        position: "fixed",
        top: 0,
        left: "50%",
        transform: "translateX(-50%)",
        zIndex: 1400,
        width: "100%",
      }}
    >
      <Alert
        severity={severity}
        variant="filled"
        onClose={onClose}
        sx={{
          minWidth: 300,
        }}
      >
        {message}
      </Alert>
    </Box>
  );
}
