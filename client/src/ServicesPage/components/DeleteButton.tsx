import { Button } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import { Service } from "../../types";

export default function DeleteButton({
  service,
  setSelectedService,
  openDelete,
}: {
  service: Service;
  setSelectedService: (service: Service) => void;
  openDelete: () => void;
}) {
  const onClick = () => {
    setSelectedService(service);
    openDelete();
  };
  return (
    <Button variant="contained" color="error" onClick={onClick}>
      <DeleteIcon />
    </Button>
  );
}
