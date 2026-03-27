import { Box, Skeleton, Stack, Paper } from "@mui/material";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function PageSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <Stack spacing={2}>
          <Skeleton variant="text" width={180} height={36} />
          <Skeleton variant="text" width={240} height={20} />
          <Stack direction="row" spacing={2} sx={{ mb: 1 }}>
            <Skeleton variant="rounded" width={200} height={40} />
            <Skeleton variant="rounded" width={100} height={40} />
          </Stack>
          {Array.from({ length: rows }, (_, i) => (
            <Skeleton key={i} variant="rounded" height={64} />
          ))}
        </Stack>
      </Paper>
    </Box>
  );
}
