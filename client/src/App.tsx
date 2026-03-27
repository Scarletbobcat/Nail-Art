import Calendar from "./AppointmentsPage/Calendar/Calendar.tsx";
import Employees from "./EmployeesPage/Employees.jsx";
import Services from "./ServicesPage/Services.tsx";
import Login from "./Login/Login.tsx";
import Navbar from "./Navbar/Navbar.tsx";
import MobileBottomNav from "./Navbar/MobileBottomNav.tsx";
import Search from "./AppointmentsPage/Search/Search.tsx";
import Clients from "./ClientsPage/Clients.tsx";
import {
  BrowserRouter as Router,
  Route,
  Routes,
  Navigate,
} from "react-router-dom";
import { Box, useMediaQuery, useTheme } from "@mui/material";

function AppContent() {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  return (
    <>
      <Navbar />
      <Box sx={{ pb: isMobile ? "72px" : 0 }}>
        <Routes>
          <Route path="/" element={<Navigate to="/Appointments" replace />} />
          <Route path="/Appointments" element={<Calendar />} />
          <Route path="/Clients" element={<Clients />} />
          <Route path="/Login" element={<Login />} />
          <Route path="/Employees" element={<Employees />} />
          <Route path="/Services" element={<Services />} />
          <Route path="/Appointments/Search" element={<Search />} />
        </Routes>
      </Box>
      <MobileBottomNav />
    </>
  );
}

function App() {
  return (
    <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AppContent />
    </Router>
  );
}

export default App;
