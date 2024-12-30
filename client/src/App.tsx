import Calendar from "./AppointmentsPage/Calendar/Calendar.tsx";
import Employees from "./EmployeesPage/Employees.jsx";
import Services from "./ServicesPage/Services.tsx";
import Login from "./Login/Login.tsx";
import Navbar from "./Navbar/Navbar.tsx";
import Search from "./AppointmentsPage/Search/Search.tsx";
import Clients from "./ClientsPage/Clients.tsx";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";

function App() {
  return (
    <>
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Navbar />
        <Routes>
          <Route path="/" element={<></>} />
          <Route path="/Appointments" element={<Calendar />} />
          <Route path="/Clients" element={<Clients />} />
          <Route path="/Login" element={<Login />} />
          <Route path="/Employees" element={<Employees />} />
          <Route path="/Services" element={<Services />} />
          <Route path="/Appointments/Search" element={<Search />} />
        </Routes>
      </Router>
    </>
  );
}

export default App;
