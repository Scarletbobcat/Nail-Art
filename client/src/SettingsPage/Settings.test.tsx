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

const configured: OrganizationSettings = {
  name: "Salon One",
  businessPhone: "330-555-0100",
  timezone: "America/New_York",
  smsRemindersEnabled: true,
  twilioConfigured: true,
  twilioAccountSid: "ACexisting",
  twilioPhoneNumberMasked: "•••• 4567",
};

const unconfigured: OrganizationSettings = {
  name: "Salon One",
  businessPhone: "330-555-0100",
  timezone: "America/New_York",
  smsRemindersEnabled: false,
  twilioConfigured: false,
  twilioAccountSid: null,
  twilioPhoneNumberMasked: null,
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

  it("loads profile values and disables the SMS toggle with a hint when unconfigured", async () => {
    mockGet().mockResolvedValue(unconfigured);

    renderSettings();

    expect(await screen.findByText("Add Twilio config first")).toBeInTheDocument();
    expect(screen.getByLabelText("Send SMS appointment reminders")).toBeDisabled();
    expect(screen.getByLabelText("Salon name")).toHaveValue("Salon One");
  });

  it("enables the SMS toggle once all three Twilio fields are filled", async () => {
    mockGet().mockResolvedValue(unconfigured);

    renderSettings();
    await screen.findByText("Add Twilio config first");

    fireEvent.change(screen.getByLabelText("Twilio Account SID"), { target: { value: "ACnew" } });
    fireEvent.change(screen.getByLabelText("Twilio Auth Token"), { target: { value: "tok-123" } });
    fireEvent.change(screen.getByLabelText("Twilio Phone Number"), { target: { value: "+15551230000" } });

    await waitFor(() => expect(screen.getByLabelText("Send SMS appointment reminders")).not.toBeDisabled());
    expect(screen.queryByText("Add Twilio config first")).not.toBeInTheDocument();
  });

  it("renders the auth token as a write-only password field that never shows a stored token", async () => {
    mockGet().mockResolvedValue(configured);

    renderSettings();

    const tokenField = await screen.findByLabelText("Twilio Auth Token");
    expect(tokenField).toHaveAttribute("type", "password");
    expect(tokenField).toHaveValue("");
    expect(screen.getByPlaceholderText("•••••••• (token saved)")).toBeInTheDocument();
    // configured + blank token field -> toggle is allowed (no re-typing needed)
    expect(screen.getByLabelText("Send SMS appointment reminders")).not.toBeDisabled();
  });

  it("saves only the fields that changed, sending the token only when entered", async () => {
    mockGet().mockResolvedValue(unconfigured);
    mockUpdate().mockResolvedValue(configured);

    renderSettings();
    await screen.findByText("Add Twilio config first");

    fireEvent.change(screen.getByLabelText("Salon name"), { target: { value: "Renamed" } });
    fireEvent.change(screen.getByLabelText("Twilio Account SID"), { target: { value: "ACnew" } });
    fireEvent.change(screen.getByLabelText("Twilio Auth Token"), { target: { value: "tok-123" } });
    fireEvent.change(screen.getByLabelText("Twilio Phone Number"), { target: { value: "+15551230000" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(mockUpdate()).toHaveBeenCalledTimes(1));
    expect(mockUpdate()).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Renamed",
        twilioAccountSid: "ACnew",
        twilioAuthToken: "tok-123",
        twilioPhoneNumber: "+15551230000",
      })
    );
    expect(await screen.findByText("Settings saved.")).toBeInTheDocument();
  });

  it("does not send the auth token on a profile-only save", async () => {
    mockGet().mockResolvedValue(configured);
    mockUpdate().mockResolvedValue(configured);

    renderSettings();
    const nameField = await screen.findByLabelText("Salon name");
    fireEvent.change(nameField, { target: { value: "Just A Rename" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(mockUpdate()).toHaveBeenCalledTimes(1));
    const payload = mockUpdate().mock.calls[0][0];
    expect(payload).not.toHaveProperty("twilioAuthToken");
    expect(payload.name).toBe("Just A Rename");
  });

  it("shows the gate-specific message on a 400 and preserves entered values", async () => {
    mockGet().mockResolvedValue(unconfigured);
    mockUpdate().mockRejectedValue({ response: { status: 400 } });

    renderSettings();
    await screen.findByText("Add Twilio config first");

    fireEvent.change(screen.getByLabelText("Salon name"), { target: { value: "Edited Name" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(
      await screen.findByText(
        "SMS reminders require Account SID, Auth Token, and Phone Number to be set."
      )
    ).toBeInTheDocument();
    // entered values are not wiped on failure
    expect(screen.getByLabelText("Salon name")).toHaveValue("Edited Name");
  });

  it("shows a generic retry message on a non-400 failure", async () => {
    mockGet().mockResolvedValue(configured);
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
