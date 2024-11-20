import { Button } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import { Appointment } from "../../../types";

export default function EditButton({
  app,
  setSelectedApp,
  openEdit,
}: {
  app: Appointment;
  setSelectedApp: (app: Appointment) => void;
  openEdit: () => void;
}) {
  const onClick = () => {
    setSelectedApp(app);
    openEdit();
  };
  return (
    <Button variant="contained" onClick={onClick}>
      <EditIcon />
    </Button>
  );
}
