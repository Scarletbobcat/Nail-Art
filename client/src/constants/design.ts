import type { Breakpoint } from "@mui/material";

export const MOBILE_BREAKPOINT: Breakpoint = "sm";

export const CALENDAR_COLORS = {
  border: "#e0e0e0",
  slotSelected: "#c7d2fe",
  slotHover: "#e0e7ff",
  slotDefault: "#f1f1f1",
  serviceType3: "#000000",
  showedUp: "#666666",
} as const;

export const SPACING = {
  page: { xs: 2, sm: 3, md: 4 },
  section: { xs: 1.5, sm: 2, md: 3 },
  card: { xs: 1.5, sm: 2 },
} as const;
