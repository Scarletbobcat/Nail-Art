import { Button } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import { Employee } from "../../types/Employee";

export default function EditButton({
  emp,
  setSelectedEmp,
  openEdit,
}: {
  emp: Employee;
  setSelectedEmp: (emp: Employee) => void;
  openEdit: () => void;
}) {
  const onClick = () => {
    setSelectedEmp(emp);
    openEdit();
  };
  return (
    <Button variant="contained" onClick={onClick}>
      <EditIcon />
    </Button>
  );
}
