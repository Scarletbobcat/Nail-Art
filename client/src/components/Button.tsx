import React from "react";
import { Button, Stack } from "@mui/material";

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
