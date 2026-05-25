import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import dayjs from "dayjs";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";

import { getAppointmentsByDate } from "../../../api/appointments";
import { getClients } from "../../../api/clients";
import { getAllEmployees } from "../../../api/employees";
import { getAllServices } from "../../../api/services";
import { Appointment } from "../../../types";
import { UNAVAILABILITY_BG } from "../../../utils/colors";
import MobileCalendar from "./MobileCalendar";

vi.mock("../../../api/appointments", () => ({
  createAppointment: vi.fn(),
  deleteAppointment: vi.fn(),
  editAppointment: vi.fn(),
  getAppointmentsByDate: vi.fn(),
}));

vi.mock("../../../api/clients", () => ({
  getClients: vi.fn(),
}));

vi.mock("../../../api/employees", () => ({
  getAllEmployees: vi.fn(),
}));

vi.mock("../../../api/services", () => ({
  getAllServices: vi.fn(),
}));

const orgTz = "America/New_York";
const systemTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

function queryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        gcTime: 0,
        retry: false,
      },
    },
  });
}

function wrapper({ children }: { children: ReactNode }) {
  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient()}>{children}</QueryClientProvider>
    </MemoryRouter>
  );
}

const employees = [{ id: "11111111-1111-4111-8111-111111111111", name: "Ava", color: "#3366FF" }];

const services = [
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

function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: "44444444-4444-4444-8444-444444444444",
    employeeId: employees[0].id,
    startsAt: "2026-05-26T13:30:00Z",
    endsAt: "2026-05-26T14:30:00Z",
    name: "Mina",
    phoneNumber: "555-0100",
    services: [String(services[0].id)],
    reminderSent: false,
    showedUp: false,
    ...overrides,
  };
}

function seedQueries(appointments: Appointment[]) {
  vi.mocked(getAllEmployees).mockResolvedValue(employees);
  vi.mocked(getAllServices).mockResolvedValue(services);
  vi.mocked(getAppointmentsByDate).mockResolvedValue(appointments);
  vi.mocked(getClients).mockResolvedValue([]);
}

describe("MobileCalendar timezone layout", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    seedQueries([appointment()]);
  });

  it("lays out a 13:30Z appointment at the 9:30 AM New York grid position", async () => {
    const startDate = dayjs("2026-05-26");

    const { container } = render(
      <MobileCalendar
        orgTz={orgTz}
        startDate={startDate}
        onDateChange={vi.fn()}
        onDateSet={vi.fn()}
      />,
      { wrapper }
    );

    await screen.findByText("Mina");
    const block = screen.getByTestId("mobile-appointment") as HTMLElement;
    const top = block?.style.top;

    if (!block || !top) {
      screen.debug(container);
    }

    expect(
      top,
      `orgTz=${orgTz} systemTz=${systemTz} startsAt=2026-05-26T13:30:00Z expectedPx=41 actualPx=${top}`
    ).toBe("41px");
  });

  it("renders unavailability markers with the shared unavailability background", async () => {
    seedQueries([
      appointment({
        id: "55555555-5555-4555-8555-555555555555",
        name: "Blocked",
        services: [services[1].id],
      }),
    ]);

    render(
      <MobileCalendar
        orgTz={orgTz}
        startDate={dayjs("2026-05-26")}
        onDateChange={vi.fn()}
        onDateSet={vi.fn()}
      />,
      { wrapper }
    );

    await waitFor(() => expect(screen.getByTestId("mobile-appointment")).toBeInTheDocument());
    const block = screen.getByTestId("mobile-appointment");

    expect(
      block,
      `orgTz=${orgTz} systemTz=${systemTz} expectedBg=${UNAVAILABILITY_BG}`
    ).toHaveStyle({ backgroundColor: UNAVAILABILITY_BG });
  });

  it("positions the now line from dayjs().tz(orgTz), not the test runner timezone", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-26T15:15:00Z"));
    seedQueries([]);

    const { container } = render(
      <MobileCalendar
        orgTz={orgTz}
        startDate={dayjs("2026-05-26")}
        onDateChange={vi.fn()}
        onDateSet={vi.fn()}
      />,
      { wrapper }
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(vi.mocked(getAppointmentsByDate)).toHaveBeenCalled();
    const nowLine = container.querySelector('[data-testid="mobile-calendar-now-line"]') as HTMLElement | null;
    const actualTop = nowLine?.style.top;

    expect(
      actualTop,
      `orgTz=${orgTz} systemTz=${systemTz} startsAt=now expectedPx=180 actualPx=${actualTop}`
    ).toBe("180px");
  });
});
