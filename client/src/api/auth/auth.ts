import api from "../api";

export const login = async (username: string, password: string) => {
  const response = await api.post("/auth/login", {
    username: username,
    password: password,
  });

  // Store the token in localStorage or sessionStorage
  localStorage.setItem("token", response.data.token);

  return response.data; // Return the response to handle the token
};

export const refreshToken = async () => {
  localStorage.removeItem("token");

  const response = await api.post("/auth/refresh");

  localStorage.setItem("token", response.data.token);

  return response.data;
};

export const logout = async () => {
  try {
    await api.post("/auth/logout");
  } catch {
    // Continue with local cleanup even if the server call fails
  }
  localStorage.removeItem("token");
  window.location.href = "/Login";
};
