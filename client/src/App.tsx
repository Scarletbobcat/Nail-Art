import Calendar from "./AppointmentsPage/Calendar/Calendar.tsx";
import Employees from "./EmployeesPage/Employees.jsx";
import Services from "./ServicesPage/Services.tsx";
import Login from "./Login/Login.tsx";
import Navbar from "./Navbar/Navbar.tsx";
import MobileBottomNav from "./Navbar/MobileBottomNav.tsx";
import Search from "./AppointmentsPage/Search/Search.tsx";
import Clients from "./ClientsPage/Clients.tsx";
import { RequireMe } from "./components/RequireMe";
import {
  BrowserRouter as Router,
  Route,
  Routes,
  Navigate,
} from "react-router-dom";
import { Box, useMediaQuery, useTheme } from "@mui/material";
import { MOBILE_BREAKPOINT } from "./constants/design";

function AppContent() {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down(MOBILE_BREAKPOINT));

  return (
    <>
      <Navbar />
      <Box sx={{ pb: isMobile ? "72px" : 0 }}>
        <Routes>
          <Route path="/" element={<Navigate to="/Appointments" replace />} />
          <Route
            path="/Appointments"
            element={
              <RequireMe>
                <Calendar />
              </RequireMe>
            }
          />
          <Route
            path="/Clients"
            element={
              <RequireMe>
                <Clients />
              </RequireMe>
            }
          />
          <Route path="/Login" element={<Login />} />
          <Route
            path="/Employees"
            element={
              <RequireMe>
                <Employees />
              </RequireMe>
            }
          />
          <Route
            path="/Services"
            element={
              <RequireMe>
                <Services />
              </RequireMe>
            }
          />
          <Route
            path="/Appointments/Search"
            element={
              <RequireMe>
                <Search />
              </RequireMe>
            }
          />
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
