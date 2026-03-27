import { ReactNode } from "react";
import {
  Modal,
  SwipeableDrawer,
  Paper,
  Box,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";

export default function ResponsiveModal({
  open,
  onClose,
  children,
  title,
  maxWidth = 545,
}: {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  title?: string;
  maxWidth?: number;
}) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  if (isMobile) {
    return (
      <SwipeableDrawer
        anchor="bottom"
        open={open}
        onOpen={() => {}}
        onClose={onClose}
        disableSwipeToOpen
        sx={{
          "& .MuiDrawer-paper": {
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            maxHeight: "90vh",
            overflow: "auto",
          },
        }}
      >
        <Box sx={{ px: 3, pt: 1.5, pb: 3 }}>
          <Box sx={{ display: "flex", justifyContent: "center", mb: 2 }}>
            <Box
              sx={{
                width: 40,
                height: 4,
                borderRadius: 2,
                bgcolor: "grey.300",
              }}
            />
          </Box>
          {title && (
            <Typography variant="h5" component="h6" sx={{ mb: 2, fontWeight: "bold" }}>
              {title}
            </Typography>
          )}
          {children}
        </Box>
      </SwipeableDrawer>
    );
  }

  return (
    <Modal open={open} onClose={onClose}>
      <Paper
        sx={{
          position: "absolute",
          top: "50%",
          left: "50%",
          transform: "translate(-50%, -50%)",
          width: { xs: "90%", sm: maxWidth },
          bgcolor: "background.paper",
          boxShadow: 24,
          p: 4,
        }}
      >
        {title && (
          <Typography variant="h5" component="h6" sx={{ mb: 3, fontWeight: "bold" }}>
            {title}
          </Typography>
        )}
        {children}
      </Paper>
    </Modal>
  );
}
