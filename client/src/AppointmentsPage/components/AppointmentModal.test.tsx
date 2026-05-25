import { fireEvent, render, screen, waitFor } from "@testing-library/react";

import { Appointment, Employee, Service } from "../../types";
import AppointmentModal from "./AppointmentModal";

const orgTz = "America/New_York";
const systemTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

const employees: Employee[] = [
  { id: "11111111-1111-4111-8111-111111111111", name: "Ava", color: "#3366FF" },
];

const services: Service[] = [
  {
    id: "22222222-2222-4222-8222-222222222222",
    name: "Gel",
    isUnavailabilityMarker: false,
  },
  {
    id: "33333333-3333-4333-8333-333333333333",
    name: "Blocked",
    isUnavailabilityMarker: true,
  },
];

const baseAppointment: Appointment = {
  id: "44444444-4444-4444-8444-444444444444",
  employeeId: employees[0].id,
  startsAt: "2026-05-26T13:30:00Z",
  endsAt: "2026-05-26T14:30:00Z",
  name: "Mina",
  phoneNumber: "555-0100",
  services: [services[0].id],
  reminderSent: false,
  showedUp: false,
};

function renderModal(overrides: Partial<Appointment> = {}) {
  const onSubmit = vi.fn();
  render(
    <AppointmentModal
      orgTz={orgTz}
      appointment={{ ...baseAppointment, ...overrides }}
      onClose={vi.fn()}
      isOpen
      renderEvents={vi.fn()}
      allServices={services}
      allEmployees={employees}
      onSubmit={onSubmit}
      type="edit"
    />
  );
  return { onSubmit };
}

describe("AppointmentModal timezone and UUID behavior", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("renders a 13:30Z existing appointment as 9:30 AM on 2026-05-26 in New York", () => {
    renderModal();

    expect(
      screen.getByLabelText(/start/i),
      `orgTz=${orgTz} systemTz=${systemTz} startsAt=${baseAppointment.startsAt}`
    ).toHaveValue(expect.stringContaining("09:30"));
    expect(
      screen.getByLabelText(/start/i),
      `orgTz=${orgTz} systemTz=${systemTz} startsAt=${baseAppointment.startsAt}`
    ).toHaveValue(expect.stringContaining("2026-05-26"));
  });

  it("blocks bookings before 9 AM in the salon timezone instead of the browser timezone", async () => {
    renderModal({ startsAt: "2026-05-26T12:30:00Z", endsAt: "2026-05-26T13:30:00Z" });

    await waitFor(() =>
      expect(
        screen.getByText(/select a time during work hours/i),
        `orgTz=${orgTz} systemTz=${systemTz} startsAt=2026-05-26T12:30:00Z`
      ).toBeInTheDocument()
    );
  });

  it("disables submit when end time is 9:00 AM for a 9:30 AM start", async () => {
    renderModal({ endsAt: "2026-05-26T13:00:00Z" });

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /edit/i }),
        `orgTz=${orgTz} systemTz=${systemTz} startsAt=${baseAppointment.startsAt} endsAt=2026-05-26T13:00:00Z`
      ).toBeDisabled()
    );
  });

  it("submits selected service UUID strings for unavailability markers", async () => {
    const { onSubmit } = renderModal({ services: [] });

    fireEvent.mouseDown(screen.getByLabelText(/services/i));
    fireEvent.click(await screen.findByText("Blocked"));
    fireEvent.click(screen.getByRole("button", { name: /edit/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][0].services, `orgTz=${orgTz} systemTz=${systemTz}`).toEqual([
      services[1].id,
    ]);
  });
});
