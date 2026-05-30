import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ReactNode } from "react";

import Settings from "./Settings";
import {
  getOrganizationSettings,
  updateOrganizationSettings,
  type OrganizationSettings,
} from "../api/organization";

vi.mock("../api/organization", () => ({
  getOrganizationSettings: vi.fn(),
  updateOrganizationSettings: vi.fn(),
}));

const configuredOn: OrganizationSettings = {
  name: "Salon One",
  businessPhone: "330-555-0100",
  timezone: "America/New_York",
  smsRemindersEnabled: true,
  twilioConfigured: true,
};

// Legacy state: flag is on in the DB but Twilio creds aren't loaded yet.
const flagOnButUnconfigured: OrganizationSettings = {
  name: "Salon One",
  businessPhone: "330-555-0100",
  timezone: "America/New_York",
  smsRemindersEnabled: true,
  twilioConfigured: false,
};

const unconfiguredBlankPhone: OrganizationSettings = {
  name: "Salon One",
  businessPhone: "",
  timezone: "America/New_York",
  smsRemindersEnabled: false,
  twilioConfigured: false,
};

function mockGet() {
  return vi.mocked(getOrganizationSettings);
}
function mockUpdate() {
  return vi.mocked(updateOrganizationSettings);
}

function createClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
}

function wrapper(client = createClient()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function renderSettings() {
  return render(<Settings />, { wrapper: wrapper() });
}

describe("Settings page", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockGet().mockReset();
    mockUpdate().mockReset();
  });

  it("never renders Twilio credential fields (operator-managed)", async () => {
    mockGet().mockResolvedValue(configuredOn);

    renderSettings();
    await screen.findByLabelText("Salon name");

    expect(screen.queryByLabelText("Twilio Account SID")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Twilio Auth Token")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Twilio Phone Number")).not.toBeInTheDocument();
  });

  it("locks the SMS toggle OFF with a hint when Twilio is not configured, even if the stored flag is on", async () => {
    mockGet().mockResolvedValue(flagOnButUnconfigured);

    renderSettings();

    const toggle = await screen.findByLabelText("Send SMS appointment reminders");
    // The stored flag is true, but unconfigured -> reads off and locked.
    expect(toggle).not.toBeChecked();
    expect(toggle).toBeDisabled();
    expect(
      screen.getByText(/SMS reminders aren't set up for this salon yet/i)
    ).toBeInTheDocument();
  });

  it("shows the SMS toggle on and editable when Twilio is configured", async () => {
    mockGet().mockResolvedValue(configuredOn);

    renderSettings();

    const toggle = await screen.findByLabelText("Send SMS appointment reminders");
    expect(toggle).toBeChecked();
    expect(toggle).not.toBeDisabled();
  });

  it("formats the business phone like the appointment search input", async () => {
    mockGet().mockResolvedValue(unconfiguredBlankPhone);

    renderSettings();
    const phone = await screen.findByLabelText("Business phone");

    fireEvent.change(phone, { target: { value: "12" } });
    expect(phone).toHaveValue("12");
    fireEvent.change(phone, { target: { value: "123" } });
    expect(phone).toHaveValue("123-"); // auto-dash after the area code
    // letters are rejected (value unchanged)
    fireEvent.change(phone, { target: { value: "123-a" } });
    expect(phone).toHaveValue("123-");
  });

  it("saves profile fields and the toggle when Twilio is configured", async () => {
    mockGet().mockResolvedValue(configuredOn);
    mockUpdate().mockResolvedValue({ ...configuredOn, smsRemindersEnabled: false });

    renderSettings();
    const toggle = await screen.findByLabelText("Send SMS appointment reminders");
    fireEvent.change(screen.getByLabelText("Salon name"), { target: { value: "Renamed" } });
    fireEvent.click(toggle); // turn off
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(mockUpdate()).toHaveBeenCalledTimes(1));
    expect(mockUpdate()).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Renamed", smsRemindersEnabled: false })
    );
    expect(await screen.findByText("Settings saved.")).toBeInTheDocument();
  });

  it("does not send the SMS flag when Twilio is not configured (leaves it untouched)", async () => {
    mockGet().mockResolvedValue(flagOnButUnconfigured);
    mockUpdate().mockResolvedValue(flagOnButUnconfigured);

    renderSettings();
    const nameField = await screen.findByLabelText("Salon name");
    fireEvent.change(nameField, { target: { value: "Just A Rename" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(mockUpdate()).toHaveBeenCalledTimes(1));
    const payload = mockUpdate().mock.calls[0][0];
    expect(payload).not.toHaveProperty("smsRemindersEnabled");
    expect(payload.name).toBe("Just A Rename");
  });

  it("shows the gate-specific message on a 400", async () => {
    mockGet().mockResolvedValue(configuredOn);
    mockUpdate().mockRejectedValue({ response: { status: 400 } });

    renderSettings();
    const nameField = await screen.findByLabelText("Salon name");
    fireEvent.change(nameField, { target: { value: "Edited" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(
      await screen.findByText(
        "SMS reminders can't be enabled until Twilio is configured for this salon."
      )
    ).toBeInTheDocument();
  });

  it("shows a generic retry message on a non-400 failure", async () => {
    mockGet().mockResolvedValue(configuredOn);
    mockUpdate().mockRejectedValue({ response: { status: 500 } });

    renderSettings();
    const nameField = await screen.findByLabelText("Salon name");
    fireEvent.change(nameField, { target: { value: "Edited" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(
      await screen.findByText("Settings could not be saved. Please try again.")
    ).toBeInTheDocument();
  });

  it("renders an error alert (not a blank form) when the initial load fails", async () => {
    mockGet().mockRejectedValue({ response: { status: 503 } });

    renderSettings();

    const alert = await screen.findByRole("alert");
    expect(
      within(alert).getByText("Could not load settings. Check your connection and refresh.")
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Salon name")).not.toBeInTheDocument();
  });
});
