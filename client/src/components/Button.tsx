import React from "react";
import { Button, Stack } from "@mui/material";
// import EditIcon from "@mui/icons-material/Edit";
// import DeleteIcon from "@mui/icons-material/Delete";
// import PlusIcon from "@mui/icons-material/Add";

export default function CustomButton({
  color,
  text,
  onClick,
  Icon,
}: {
  color: "primary" | "error" | "info";
  text?: string;
  onClick: () => void;
  Icon?: React.ElementType;
}) {
  return (
    <Button variant="contained" color={color} onClick={onClick}>
      <Stack direction="row" spacing={1} alignContent="center">
        {text}
        {Icon && <Icon />}
      </Stack>
    </Button>
  );
}
