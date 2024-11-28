import { Button, Stack } from "@mui/material";
import PlusIcon from "@mui/icons-material/Add";

export default function DeleteButton({
  openCreate,
}: {
  openCreate: () => void;
}) {
  const onClick = () => {
    openCreate();
  };
  return (
    <Button variant="contained" color="primary" onClick={onClick}>
      <Stack direction="row" spacing={1} alignContent="center">
        Create
        <PlusIcon />
      </Stack>
    </Button>
  );
}
