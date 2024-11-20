import { Button } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import { Appointment } from "../../../types";

export default function DeleteButton({
  app,
  setSelectedApp,
  openDelete,
}: {
  app: Appointment;
  setSelectedApp: (app: Appointment) => void;
  openDelete: () => void;
}) {
  const onClick = () => {
    setSelectedApp(app);
    openDelete();
  };
  return (
    <Button variant="contained" color="error" onClick={onClick}>
      <DeleteIcon />
    </Button>
  );
}
