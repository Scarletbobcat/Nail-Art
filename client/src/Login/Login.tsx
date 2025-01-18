import {
  FormControl,
  Typography,
  Card,
  Box,
  TextField,
  FormLabel,
  Button,
  Container,
  CircularProgress,
} from "@mui/material";
import { login } from "../api/auth/auth";
import { useNavigate } from "react-router-dom";
import { AxiosError } from "axios";
import { useState, FormEvent, ChangeEvent } from "react";
export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    try {
      // Redirect to a protected page after successful login
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
      // setting error to display
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

  function handleUsernameChange(e: ChangeEvent<HTMLInputElement>) {
    setUsername(e.target.value);
  }

  function handlePasswordChange(e: ChangeEvent<HTMLInputElement>) {
    setPassword(e.target.value);
  }

  return (
    <>
      <Container>
        <Card
          variant="outlined"
          sx={{
            position: "absolute",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            width: { xs: "90%", sm: 545 },
            bgcolor: "background.paper",
            boxShadow: 24,
            p: 4,
          }}
        >
          <Typography
            variant="h4"
            sx={{
              mb: 2,
            }}
          >
            Login
          </Typography>
          <Box
            component="form"
            onSubmit={handleSubmit}
            sx={{
              display: "flex",
              flexDirection: "column",
              width: "100%",
              gap: 2,
            }}
          >
            <FormControl>
              <FormLabel htmlFor="username">Username</FormLabel>
              <TextField
                type="username"
                name="email"
                placeholder="username"
                required
                variant="outlined"
                onChange={handleUsernameChange}
              />
            </FormControl>
            <FormControl>
              <FormLabel htmlFor="password">Password</FormLabel>
              <TextField
                required
                fullWidth
                name="password"
                placeholder="••••••"
                type="password"
                id="password"
                autoComplete="password"
                variant="outlined"
                onChange={handlePasswordChange}
              />
            </FormControl>
            <Typography color="red">{error}</Typography>
            <FormControl>
              <Button
                type="submit"
                variant="contained"
                disabled={isLoading}
                endIcon={isLoading && <CircularProgress size={20} />}
              >
                Login
              </Button>
            </FormControl>
          </Box>
        </Card>
      </Container>
    </>
  );
}
