import axios from "axios";
const BASE_URL = import.meta.env.VITE_API_URL;

// Create an Axios instance
const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
});

// Add a request interceptor to set headers
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
      config.headers["Content-Type"] = "application/json";
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response.status === 403) {
      localStorage.setItem("previousUrl", window.location.pathname);
      window.location.href = "/login";
    }
  }
);

export default api;
