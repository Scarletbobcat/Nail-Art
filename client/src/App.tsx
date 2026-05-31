import Calendar from "./AppointmentsPage/Calendar/Calendar.tsx";
import Employees from "./EmployeesPage/Employees.jsx";
import Services from "./ServicesPage/Services.tsx";
import Login from "./Login/Login.tsx";
import Navbar from "./Navbar/Navbar.tsx";
import MobileBottomNav from "./Navbar/MobileBottomNav.tsx";
import Search from "./AppointmentsPage/Search/Search.tsx";
import Clients from "./ClientsPage/Clients.tsx";
import Settings from "./SettingsPage/Settings.tsx";
import AdminOrganizations from "./AdminPage/Organizations.tsx";
import AdminOrganizationDetail from "./AdminPage/OrganizationDetail.tsx";
import AdminCreateOrganization from "./AdminPage/CreateOrganization.tsx";
import { RequireMe } from "./components/RequireMe";
import {
  BrowserRouter as Router,
  Route,
  Routes,
  Navigate,
  useLocation,
} from "react-router-dom";
import { Box, useMediaQuery, useTheme } from "@mui/material";
import { MOBILE_BREAKPOINT } from "./constants/design";
import { ROUTES } from "./constants/routes";

function AppContent() {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down(MOBILE_BREAKPOINT));
  const location = useLocation();
  // The login page is the only unauthenticated route. Hiding the nav there keeps
  // its useMe() lookups from firing without a token, which would 401 -> redirect
  // to /login -> remount -> loop.
  const showChrome = location.pathname.toLowerCase() !== ROUTES.login;

  return (
    <>
      {showChrome && <Navbar />}
      <Box sx={{ pb: isMobile && showChrome ? "72px" : 0 }}>
        <Routes>
          <Route path={ROUTES.home} element={<Navigate to={ROUTES.appointments} replace />} />
          <Route
            path={ROUTES.appointments}
            element={
              <RequireMe>
                <Calendar />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.clients}
            element={
              <RequireMe>
                <Clients />
              </RequireMe>
            }
          />
          <Route path={ROUTES.login} element={<Login />} />
          <Route
            path={ROUTES.employees}
            element={
              <RequireMe>
                <Employees />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.services}
            element={
              <RequireMe>
                <Services />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.appointmentsSearch}
            element={
              <RequireMe>
                <Search />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.settings}
            element={
              <RequireMe requiredRole="owner">
                <Settings />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.admin}
            element={
              <RequireMe requirePlatformAdmin>
                <AdminOrganizations />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.adminNew}
            element={
              <RequireMe requirePlatformAdmin>
                <AdminCreateOrganization />
              </RequireMe>
            }
          />
          <Route
            path={ROUTES.adminOrganization}
            element={
              <RequireMe requirePlatformAdmin>
                <AdminOrganizationDetail />
              </RequireMe>
            }
          />
        </Routes>
      </Box>
      {showChrome && <MobileBottomNav />}
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
