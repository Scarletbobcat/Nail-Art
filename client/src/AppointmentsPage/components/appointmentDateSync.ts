import { type Dayjs } from "dayjs";

import { type Appointment } from "../../types";
import { toIsoFromSalonInput } from "../../utils/datetime";

export function appointmentWithStartChange({
  form,
  nextStart,
  currentStart,
  currentEnd,
  orgTz,
}: {
  form: Appointment;
  nextStart: Dayjs;
  currentStart: Dayjs;
  currentEnd: Dayjs;
  orgTz: string;
}): Appointment {
  const nextDate = nextStart.format("YYYY-MM-DD");
  const startDateChanged = !nextStart.isSame(currentStart, "day");

  return {
    ...form,
    startsAt: toIsoFromSalonInput(nextDate, nextStart.format("HH:mm:ss"), orgTz),
    endsAt: startDateChanged
      ? toIsoFromSalonInput(nextDate, currentEnd.format("HH:mm:ss"), orgTz)
      : form.endsAt,
  };
}

export function startForEndChangeValidation({
  nextEnd,
  currentStart,
  currentEnd,
}: {
  nextEnd: Dayjs;
  currentStart: Dayjs;
  currentEnd: Dayjs;
}): Dayjs {
  if (nextEnd.isSame(currentEnd, "day")) {
    return currentStart;
  }

  return nextEnd
    .hour(currentStart.hour())
    .minute(currentStart.minute())
    .second(currentStart.second())
    .millisecond(currentStart.millisecond());
}

export function appointmentWithEndChange({
  form,
  nextEnd,
  currentStart,
  currentEnd,
  orgTz,
}: {
  form: Appointment;
  nextEnd: Dayjs;
  currentStart: Dayjs;
  currentEnd: Dayjs;
  orgTz: string;
}): Appointment {
  const nextDate = nextEnd.format("YYYY-MM-DD");
  const endDateChanged = !nextEnd.isSame(currentEnd, "day");

  return {
    ...form,
    startsAt: endDateChanged
      ? toIsoFromSalonInput(nextDate, currentStart.format("HH:mm:ss"), orgTz)
      : form.startsAt,
    endsAt: toIsoFromSalonInput(nextDate, nextEnd.format("HH:mm:ss"), orgTz),
  };
}
