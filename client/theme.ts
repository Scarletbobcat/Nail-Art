import { createTheme } from "@mui/material/styles";

const defaultTheme = createTheme();

const theme = createTheme({
  shape: {
    borderRadius: 12,
  },
  palette: {
    primary: {
      main: "#3b82f6",
      light: "#60a5fa",
      dark: "#2563eb",
    },
    secondary: {
      main: "#f43f5e",
      light: "#fb7185",
      dark: "#e11d48",
    },
    background: {
      default: "#f8fafc",
      paper: "#ffffff",
    },
    info: {
      main: "#606060",
    },
    text: {
      primary: "#1e293b",
      secondary: "#64748b",
    },
    divider: "#e2e8f0",
  },
  typography: {
    fontFamily: "'Inter', Roboto, Arial, sans-serif",
    h4: { fontWeight: 700 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 500 },
    body2: { color: "#64748b" },
  },
  shadows: [
    ...defaultTheme.shadows.map((s, i) => {
      if (i === 1) return "0 1px 2px 0 rgb(0 0 0 / 0.05)";
      if (i === 2) return "0 1px 3px 0 rgb(0 0 0 / 0.08), 0 1px 2px -1px rgb(0 0 0 / 0.06)";
      if (i === 3) return "0 4px 6px -1px rgb(0 0 0 / 0.08), 0 2px 4px -2px rgb(0 0 0 / 0.05)";
      if (i === 4) return "0 10px 15px -3px rgb(0 0 0 / 0.08), 0 4px 6px -4px rgb(0 0 0 / 0.04)";
      if (i === 5) return "0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.06)";
      if (i === 6) return "0 25px 50px -12px rgb(0 0 0 / 0.2)";
      return s;
    }),
  ] as typeof defaultTheme.shadows,
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundImage:
            "radial-gradient(ellipse at 20% 0%, rgba(59, 130, 246, 0.06) 0%, transparent 50%)," +
            "radial-gradient(ellipse at 80% 100%, rgba(244, 63, 94, 0.04) 0%, transparent 50%)",
        },
      },
    },
    MuiPaper: {
      defaultProps: {
        elevation: 0,
      },
      styleOverrides: {
        root: {
          border: "1px solid rgba(226, 232, 240, 0.6)",
          boxShadow:
            "0 1px 3px 0 rgb(0 0 0 / 0.04), 0 1px 2px -1px rgb(0 0 0 / 0.03)",
          backgroundImage: "none",
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: "none" as const,
          fontWeight: 600,
          borderRadius: 8,
        },
        contained: {
          backgroundColor: "#3b82f6",
          boxShadow: "0 1px 3px 0 rgb(37 99 235 / 0.3), 0 1px 2px -1px rgb(37 99 235 / 0.2)",
          "&:hover": {
            backgroundColor: "#2563eb",
            boxShadow: "0 4px 12px -2px rgb(37 99 235 / 0.4)",
          },
        },
        containedError: {
          backgroundColor: "#f43f5e",
          boxShadow: "0 1px 3px 0 rgb(225 29 72 / 0.3)",
          "&:hover": {
            backgroundColor: "#e11d48",
            boxShadow: "0 4px 12px -2px rgb(225 29 72 / 0.4)",
          },
        },
      },
    },
    MuiFab: {
      styleOverrides: {
        root: {
          backgroundColor: "#3b82f6",
          boxShadow: "0 4px 14px 0 rgb(59 130 246 / 0.4)",
          "&:hover": {
            backgroundColor: "#2563eb",
            boxShadow: "0 6px 20px 0 rgb(59 130 246 / 0.5)",
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 500,
        },
        filled: {
          boxShadow: "0 1px 2px 0 rgb(0 0 0 / 0.05)",
        },
      },
    },
  },
});

export default theme;
