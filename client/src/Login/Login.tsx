import {
  Typography,
  Box,
  TextField,
  Button,
  Paper,
  Stack,
  CircularProgress,
  IconButton,
  InputAdornment,
} from "@mui/material";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";
import { login } from "../api/auth/auth";
import { useNavigate } from "react-router-dom";
import { AxiosError } from "axios";
import { useState, FormEvent } from "react";
import AnimatedPage from "../components/AnimatedPage";

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const previousUrl = localStorage.getItem("previousUrl");
      setIsLoading(true);
      await login(username, password);
      setIsLoading(false);
      if (previousUrl) {
        navigate(previousUrl);
        localStorage.removeItem("previousUrl");
      } else {
        navigate("/");
      }
    } catch (error) {
      const errors = error as AxiosError;
      setIsLoading(false);
      if (
        errors.response?.status === 401 &&
        (errors.response?.data as { detail: string }).detail ===
          "Bad credentials"
      ) {
        setError("Invalid username or password");
      } else {
        setError("An error occurred. Please try again later.");
      }
    }
  }

  return (
    <AnimatedPage>
      <Box
        sx={{
          minHeight: "calc(100vh - 64px)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          p: { xs: 2, sm: 4 },
          backgroundColor: "#3b82f6",
          position: "relative",
          overflow: "hidden",
          "&::before": {
            content: '""',
            position: "absolute",
            top: "-50%",
            right: "-20%",
            width: "60%",
            height: "150%",
            background: "radial-gradient(ellipse, rgba(255,255,255,0.08) 0%, transparent 70%)",
            pointerEvents: "none",
          },
        }}
      >
        <Paper
          sx={{
            width: "100%",
            maxWidth: 400,
            p: { xs: 3, sm: 4 },
            boxShadow: "0 25px 50px -12px rgb(0 0 0 / 0.25), 0 0 0 1px rgb(255 255 255 / 0.05)",
            border: "1px solid rgba(255, 255, 255, 0.1)",
          }}
        >
          <Stack spacing={1} sx={{ mb: 3 }}>
            <Typography variant="h5" fontWeight={700}>
              Welcome back
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Sign in to manage your appointments
            </Typography>
          </Stack>
          <Box
            component="form"
            onSubmit={handleSubmit}
            sx={{ display: "flex", flexDirection: "column", gap: 2 }}
          >
            <TextField
              label="Username"
              name="username"
              required
              fullWidth
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <TextField
              label="Password"
              name="password"
              required
              fullWidth
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={() => setShowPassword(!showPassword)}
                        edge="end"
                        size="small"
                      >
                        {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />
            {error && (
              <Typography color="error" variant="body2">
                {error}
              </Typography>
            )}
            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={isLoading}
              endIcon={isLoading ? <CircularProgress size={20} /> : null}
              sx={{ mt: 1 }}
            >
              Sign in
            </Button>
          </Box>
        </Paper>
      </Box>
    </AnimatedPage>
  );
}
