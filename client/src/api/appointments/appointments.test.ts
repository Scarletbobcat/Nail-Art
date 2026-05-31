import api from "../api";
import { getAppointmentsByPhoneNumber } from "./appointments";

vi.mock("../api", () => ({
  default: {
    get: vi.fn(),
  },
}));

describe("getAppointmentsByPhoneNumber", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not call the API for blank phone searches", async () => {
    await expect(getAppointmentsByPhoneNumber("   ")).resolves.toEqual([]);

    expect(api.get).not.toHaveBeenCalled();
  });

  it("encodes the searched phone number in the URL path", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [{ id: "appt-1" }] });

    await expect(getAppointmentsByPhoneNumber("330 555+1000")).resolves.toEqual([
      { id: "appt-1" },
    ]);

    expect(api.get).toHaveBeenCalledWith(
      "/appointments/search/330%20555%2B1000"
    );
  });
});
