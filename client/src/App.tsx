import Calendar from "./AppointmentsPage/Calendar/Calendar.tsx";
import Home from "./Home/Home.tsx";
import Employees from "./EmployeesPage/Employees.jsx";
import Services from "./ServicesPage/Services.tsx";
import Login from "./Login/Login.tsx";
import Navbar from "./Navbar/Navbar.tsx";
import Search from "./AppointmentsPage/Search/Search.tsx";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";

function App() {
  return (
    <>
      <Router>
        <Navbar />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/Appointments" element={<Calendar />} />
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
