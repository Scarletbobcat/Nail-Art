import {
  FormControl,
  Typography,
  Card,
  Box,
  TextField,
  FormLabel,
  Button,
  Container,
} from "@mui/material";
import { login } from "../api/auth/auth";
import { useNavigate } from "react-router-dom";
import { useState, FormEvent, ChangeEvent } from "react";
export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      // Redirect to a protected page after successful login
      const previousUrl = localStorage.getItem("previousUrl");
      await login(username, password);
      if (previousUrl) {
        navigate(previousUrl);
        localStorage.removeItem("previousUrl");
      } else {
        navigate("/");
      }
    } catch (e) {
      console.log(e);
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
            <Typography
              sx={{
                textAlign: "right",
              }}
            >
              <span
                style={{
                  cursor: "pointer",
                }}
              >
                Register
              </span>
            </Typography>
            <FormControl>
              <Button type="submit" variant="contained">
                Login
              </Button>
            </FormControl>
          </Box>
        </Card>
      </Container>
    </>
  );
}
