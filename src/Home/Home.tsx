import { Button } from "@mui/material";
import { refreshToken } from "../api/auth/auth";

export default function Home() {
  return (
    <>
      <div>
        <p>To Do:</p>
        <ul>
          <li>Create CRUD pages for Employees and Services</li>
          <li>Appointment reminder texts (automatic if possible)</li>
        </ul>
      </div>
      <Button variant="contained" onClick={refreshToken}>
        Get new access token
      </Button>
    </>
  );
}
