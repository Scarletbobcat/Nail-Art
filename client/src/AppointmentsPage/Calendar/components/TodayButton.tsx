import { Button } from "@mui/material";
export default function TodayButton({ onClick }: { onClick: () => void }) {
  return (
    <Button variant="contained" color="primary" onClick={onClick}>
      Today
    </Button>
  );
}
