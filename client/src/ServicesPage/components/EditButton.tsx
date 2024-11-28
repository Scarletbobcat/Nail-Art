import { Button } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import { Service } from "../../types";

export default function EditButton({
  service,
  setSelectedService,
  openEdit,
}: {
  service: Service;
  setSelectedService: (service: Service) => void;
  openEdit: () => void;
}) {
  const onClick = () => {
    setSelectedService(service);
    openEdit();
  };
  return (
    <Button variant="contained" onClick={onClick}>
      <EditIcon />
    </Button>
  );
}
