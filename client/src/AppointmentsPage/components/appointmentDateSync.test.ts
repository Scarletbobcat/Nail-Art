import { type Appointment } from "../../types";
import { dateTimeInSalon } from "../../utils/datetime";
import {
  appointmentWithEndChange,
  appointmentWithStartChange,
  startForEndChangeValidation,
} from "./appointmentDateSync";

const orgTz = "America/New_York";

const baseAppointment: Appointment = {
  id: "44444444-4444-4444-8444-444444444444",
  employeeId: "11111111-1111-4111-8111-111111111111",
  startsAt: "2026-05-26T13:30:00Z",
  endsAt: "2026-05-26T14:30:00Z",
  name: "Mina",
  phoneNumber: "555-0100",
  services: ["22222222-2222-4222-8222-222222222222"],
  reminderSent: false,
  showedUp: false,
};

function salonDateTime(iso: string) {
  return dateTimeInSalon(iso, orgTz).format("YYYY-MM-DD HH:mm:ss");
}

describe("appointment date sync", () => {
  it("matches the end date to a changed start date while preserving both times", () => {
    const currentStart = dateTimeInSalon(baseAppointment.startsAt, orgTz);
    const currentEnd = dateTimeInSalon(baseAppointment.endsAt, orgTz);
    const nextStart = currentStart.add(2, "day");

    const updated = appointmentWithStartChange({
      form: baseAppointment,
      nextStart,
      currentStart,
      currentEnd,
      orgTz,
    });

    expect(salonDateTime(updated.startsAt)).toBe("2026-05-28 09:30:00");
    expect(salonDateTime(updated.endsAt)).toBe("2026-05-28 10:30:00");
  });

  it("matches the start date to a changed end date while preserving both times", () => {
    const currentStart = dateTimeInSalon(baseAppointment.startsAt, orgTz);
    const currentEnd = dateTimeInSalon(baseAppointment.endsAt, orgTz);
    const nextEnd = currentEnd.add(3, "day");

    const updated = appointmentWithEndChange({
      form: baseAppointment,
      nextEnd,
      currentStart,
      currentEnd,
      orgTz,
    });

    expect(salonDateTime(updated.startsAt)).toBe("2026-05-29 09:30:00");
    expect(salonDateTime(updated.endsAt)).toBe("2026-05-29 10:30:00");
  });

  it("validates changed end dates against the matched start date", () => {
    const currentStart = dateTimeInSalon(baseAppointment.startsAt, orgTz);
    const currentEnd = dateTimeInSalon(baseAppointment.endsAt, orgTz);
    const nextEnd = currentEnd.add(1, "day");

    const validationStart = startForEndChangeValidation({
      nextEnd,
      currentStart,
      currentEnd,
    });

    expect(validationStart.format("YYYY-MM-DD HH:mm:ss")).toBe(
      "2026-05-27 09:30:00"
    );
  });
});
