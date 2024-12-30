import React from "react";
import { Button, Stack, SxProps, Typography, Theme } from "@mui/material";

export default function CustomButton({
  color,
  text,
  onClick,
  Icon,
  sx,
}: {
  color?: "primary" | "error" | "info" | "secondary";
  text?: string;
  onClick: () => void;
  Icon?: React.ElementType;
  sx?: SxProps<Theme>;
}) {
  return (
    <Button variant="text" color={color} onClick={onClick} sx={{ ...sx }}>
      <Stack direction="row" spacing={1} alignContent="center">
        <Typography>{text}</Typography>
        {Icon && <Icon />}
      </Stack>
    </Button>
  );
}
