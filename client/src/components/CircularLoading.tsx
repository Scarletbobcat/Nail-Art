import { Box, CircularProgress } from "@mui/material";
import { SxProps } from "@mui/system";

export default function CircularLoading({ ...sx }: { sx?: SxProps }) {
  return (
    <Box
      sx={{
        ...sx,
        position: "absolute",
        top: "50%",
        left: "50%",
      }}
    >
      <CircularProgress />
    </Box>
  );
}
