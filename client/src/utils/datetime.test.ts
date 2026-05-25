import dayjs from "dayjs";

import {
  businessHoursMax,
  businessHoursMin,
  formatDate,
  formatDateTime,
  formatTime,
  minutesFromMidnight,
  toIsoFromSalonInput,
} from "./datetime";

const orgTz = "America/New_York";
const systemTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

function tzContext(input: string, actual?: unknown) {
  return `orgTz=${orgTz} systemTz=${systemTz} input=${input} actual=${String(actual)}`;
}

describe("datetime timezone helpers", () => {
  it("formatTime returns 9:30 AM for a 13:30Z instant in America/New_York", () => {
    const input = "2026-05-26T13:30:00Z";

    const actual = formatTime(input, orgTz);

    expect(actual, tzContext(input, actual)).toBe("9:30 AM");
  });

  it("formatTime returns 6:30 AM for the same instant in America/Los_Angeles", () => {
    const input = "2026-05-26T13:30:00Z";
    const laTz = "America/Los_Angeles";

    const actual = formatTime(input, laTz);

    expect(actual, `orgTz=${laTz} systemTz=${systemTz} input=${input} actual=${actual}`).toBe(
      "6:30 AM"
    );
  });

  it("toIsoFromSalonInput converts 2026-05-26 09:30 New York wall time to 13:30Z", () => {
    const actual = toIsoFromSalonInput("2026-05-26", "09:30", orgTz);

    expect(actual, tzContext("2026-05-26 09:30", actual)).toBe("2026-05-26T13:30:00.000Z");
  });

  it("minutesFromMidnight returns 570 for a 9:30 AM New York appointment", () => {
    const input = "2026-05-26T13:30:00Z";

    const actual = minutesFromMidnight(input, orgTz);

    expect(actual, tzContext(input, actual)).toBe(570);
  });

  it("formatTime shows the previous salon calendar day wall time when UTC date crosses midnight", () => {
    const input = "2026-05-26T00:30:00Z";

    const actual = formatTime(input, orgTz);

    expect(actual, tzContext(input, actual)).toBe("8:30 PM");
  });

  it("throws instead of falling back to browser timezone when orgTz is missing", () => {
    const input = "2026-05-26T13:30:00Z";

    expect(() => formatTime(input, undefined), tzContext(input)).toThrow();
    expect(() => formatDate(input, undefined), tzContext(input)).toThrow();
    expect(() => formatDateTime(input, undefined), tzContext(input)).toThrow();
    expect(() => toIsoFromSalonInput("2026-05-26", "09:30", undefined), tzContext(input)).toThrow();
    expect(() => minutesFromMidnight(input, undefined), tzContext(input)).toThrow();
    expect(() => businessHoursMin(undefined), "businessHoursMin missing orgTz").toThrow();
    expect(() => businessHoursMax(undefined), "businessHoursMax missing orgTz").toThrow();
  });

  it("round-trips a New York form date and time through ISO and back to the same wall time", () => {
    const iso = toIsoFromSalonInput("2026-05-26", "09:30", orgTz);

    const actualTime = formatTime(iso, orgTz);
    const actualDate = formatDate(iso, orgTz);

    expect(actualTime, tzContext(iso, actualTime)).toBe("9:30 AM");
    expect(actualDate, tzContext(iso, actualDate)).toBe("2026-05-26");
  });

  it("businessHoursMin returns a dayjs value at 9:00 AM in the salon timezone", () => {
    const actual = businessHoursMin(orgTz);

    expect(dayjs.isDayjs(actual), `orgTz=${orgTz} actual=${String(actual)}`).toBe(true);
    expect(actual.tz(orgTz).format("HH:mm"), `orgTz=${orgTz} systemTz=${systemTz}`).toBe("09:00");
  });

  it("businessHoursMax returns a dayjs value at 9:00 PM in the salon timezone", () => {
    const actual = businessHoursMax(orgTz);

    expect(dayjs.isDayjs(actual), `orgTz=${orgTz} actual=${String(actual)}`).toBe(true);
    expect(actual.tz(orgTz).format("HH:mm"), `orgTz=${orgTz} systemTz=${systemTz}`).toBe("21:00");
  });
});
