import axios, { AxiosRequestConfig } from "axios";
import { refreshToken } from "./auth/auth";
const BASE_URL = import.meta.env.VITE_API_URL;

declare module "axios" {
  interface AxiosRequestConfig {
    _retry?: boolean; // Add the _retry property to the request config
  }
}

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
      config.headers["Authorization"] = !config._retry
        ? `Bearer ${token}`
        : config.headers["Authorization"];
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    console.log(error);
    if (error.response.status === 401) {
      if (
        error.response.data == "Invalid or expired refresh token" ||
        error.response.data == "Refresh token is missing" ||
        error.response.data == "User not found" ||
        error.config._retry
      ) {
        localStorage.removeItem("token");
        localStorage.setItem("previousUrl", window.location.pathname);
        window.location.href = "/login";
      } else {
        try {
          const originalRequest: AxiosRequestConfig = error.config;

          await refreshToken();

          originalRequest.headers = originalRequest.headers ?? {};

          originalRequest._retry = true;

          originalRequest.headers[
            "Authorization"
          ] = `Bearer ${localStorage.getItem("token")}`;
          return api(originalRequest);
        } catch (error) {
          console.error(error);
          localStorage.setItem("previousUrl", window.location.pathname);
          window.location.href = "/login";
        }
      }
    }
    if (error.response.status === 403) {
      localStorage.setItem("previousUrl", window.location.pathname);
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

export default api;
