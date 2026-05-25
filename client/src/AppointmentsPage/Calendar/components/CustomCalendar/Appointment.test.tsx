import { render, screen } from "@testing-library/react";

import { Appointment } from "../../../../types";
import { UNAVAILABILITY_BG } from "../../../../utils/colors";
import CustomAppointment from "./Appointment";

const baseAppointment: Appointment = {
  id: "11111111-1111-4111-8111-111111111111",
  employeeId: "22222222-2222-4222-8222-222222222222",
  startsAt: "2026-05-26T13:30:00Z",
  endsAt: "2026-05-26T14:30:00Z",
  name: "Mina",
  services: ["33333333-3333-4333-8333-333333333333"],
  phoneNumber: "555-0100",
  reminderSent: false,
  showedUp: false,
};

describe("CustomAppointment desktop styling", () => {
  it("uses the passed bgcolor for special appointments", () => {
    render(<CustomAppointment appointment={baseAppointment} bgcolor={UNAVAILABILITY_BG} isSpecial />);

    expect(screen.getByTestId("desktop-appointment")).toHaveStyle({
      backgroundColor: UNAVAILABILITY_BG,
    });
  });

  it("preserves default styling when bgcolor is undefined", () => {
    render(<CustomAppointment appointment={baseAppointment} />);

    expect(screen.getByTestId("desktop-appointment")).not.toHaveStyle({
      backgroundColor: UNAVAILABILITY_BG,
    });
  });
});
