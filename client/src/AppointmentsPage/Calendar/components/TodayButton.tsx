import { Button } from "@mui/material";
import TodayIcon from "@mui/icons-material/Today";

export default function TodayButton({ onClick }: { onClick: () => void }) {
  return (
    <Button
      variant="contained"
      color="primary"
      onClick={onClick}
      startIcon={<TodayIcon />}
      fullWidth
    >
      Today
    </Button>
  );
}
