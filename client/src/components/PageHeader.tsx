import { ReactNode } from "react";
import { Stack, Box, Typography } from "@mui/material";

export default function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <Stack
      direction={{ xs: "column", sm: "row" }}
      justifyContent="space-between"
      alignItems={{ xs: "stretch", sm: "center" }}
      spacing={1}
      sx={{ mb: 3 }}
    >
      <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5 }}>
        <Box
          sx={{
            width: 4,
            alignSelf: "stretch",
            borderRadius: 2,
            backgroundColor: "#3b82f6",
            flexShrink: 0,
          }}
        />
        <Box>
          <Typography variant="h5" fontWeight={700} sx={{ letterSpacing: "-0.01em" }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
      {action && <Box>{action}</Box>}
    </Stack>
  );
}
