import RefreshIcon from "@mui/icons-material/Refresh";
import { Box, Button, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import { useMe } from "../hooks/useMe";
import { getHttpStatus } from "../utils/httpError";
import { ROUTES } from "../constants/routes";
import CircularLoading from "./CircularLoading";

type RequireMeProps = {
  children: ReactNode;
  // When set, the route also requires this role; a mismatch redirects to
  // `redirectTo`. Omitting it preserves the plain authenticated-only behavior.
  requiredRole?: string;
  // When true, the route requires a platform admin (the org-less operator). When
  // false/omitted, platform admins are redirected to /admin — salon pages can't
  // function without an org, so admins are corralled into the console.
  requirePlatformAdmin?: boolean;
  redirectTo?: string;
};

type RetryFallbackProps = {
  onRetry: () => void;
};

export function RequireMe({
  children,
  requiredRole,
  requirePlatformAdmin,
  redirectTo = ROUTES.appointments,
}: RequireMeProps) {
  const { data, error, isError, isLoading, isPending, refetch } = useMe();

  if (isLoading || isPending) {
    return <CircularLoading />;
  }

  if (isError) {
    if (getHttpStatus(error) === 401) {
      return <Navigate to={ROUTES.login} replace />;
    }

    if (!data) {
      return <RetryFallback onRetry={() => void refetch()} />;
    }
    // fall through to the gate checks below, using cached data
  }

  if (!data) {
    return <CircularLoading />;
  }

  if (requirePlatformAdmin) {
    // Admin-only route: non-admins go back to their salon home.
    if (!data.user.isPlatformAdmin) {
      return <Navigate to={redirectTo} replace />;
    }
    return <>{children}</>;
  }

  // Salon route: a platform admin has no org and can't use it — send to the console.
  if (data.user.isPlatformAdmin) {
    return <Navigate to={ROUTES.admin} replace />;
  }

  if (requiredRole && data.user.role !== requiredRole) {
    return <Navigate to={redirectTo} replace />;
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
