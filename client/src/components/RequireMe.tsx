import RefreshIcon from "@mui/icons-material/Refresh";
import { Box, Button, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import { useMe } from "../hooks/useMe";
import { getHttpStatus } from "../utils/httpError";
import CircularLoading from "./CircularLoading";

type RequireMeProps = {
  children: ReactNode;
};

type RetryFallbackProps = {
  onRetry: () => void;
};

export function RequireMe({ children }: RequireMeProps) {
  const { data, error, isError, isLoading, isPending, refetch } = useMe();

  if (isLoading || isPending) {
    return <CircularLoading />;
  }

  if (isError) {
    if (getHttpStatus(error) === 401) {
      return <Navigate to="/Login" replace />;
    }

    if (data) {
      return <>{children}</>;
    }

    return <RetryFallback onRetry={() => void refetch()} />;
  }

  if (!data) {
    return <CircularLoading />;
  }

  return <>{children}</>;
}

function RetryFallback({ onRetry }: RetryFallbackProps) {
  return (
    <Box
      sx={{
        minHeight: "calc(100vh - 64px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 2,
        py: 6,
      }}
    >
      <Stack
        spacing={2}
        sx={{
          alignItems: "center",
          textAlign: "center",
          maxWidth: 420,
        }}
      >
        <Typography variant="h6" fontWeight={700}>
          Unable to reach the server. Tap to retry.
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Your session is still here. This is usually a temporary connection issue.
        </Typography>
        <Button
          variant="contained"
          size="large"
          startIcon={<RefreshIcon />}
          onClick={onRetry}
          sx={{ mt: 1, minWidth: 132 }}
        >
          Retry
        </Button>
      </Stack>
    </Box>
  );
}
