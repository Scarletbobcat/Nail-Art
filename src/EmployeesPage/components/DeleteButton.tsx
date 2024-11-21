import { Button } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import { Employee } from "../../types/Employee";

export default function DeleteButton({
  emp,
  setSelectedEmp,
  openDelete,
}: {
  emp: Employee;
  setSelectedEmp: (emp: Employee) => void;
  openDelete: () => void;
}) {
  const onClick = () => {
    setSelectedEmp(emp);
    openDelete();
  };
  return (
    <Button variant="contained" color="error" onClick={onClick}>
      <DeleteIcon />
    </Button>
  );
}
