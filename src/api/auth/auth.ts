import api from "../api";

export const login = async (username: string, password: string) => {
  try {
    const response = await api.post("/auth/login", {
      username: username,
      password: password,
    });

    // Store the token in localStorage or sessionStorage
    localStorage.setItem("token", response.data.token);

    return response.data; // Return the response to handle the token
  } catch (error) {
    console.error("Login failed:", error);
    throw error; // Throw error to handle it in the component
  }
};

export const refreshToken = async () => {
  try {
    const response = await api.post("/auth/refresh");

    localStorage.setItem("token", response.data.token);
  } catch (error) {
    console.error("Refresh token failed:", error);
    throw error;
  }
};
