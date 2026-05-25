import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);
dayjs.extend(timezone);

function requireTz(orgTz: string | undefined): asserts orgTz is string {
  if (!orgTz) {
    throw new Error("orgTz is required; never fall back to browser timezone");
  }
}

export function formatTime(iso: string, orgTz: string | undefined): string {
  requireTz(orgTz);
  return dayjs(iso).tz(orgTz).format("h:mm A");
}

export function formatDate(iso: string, orgTz: string | undefined): string {
  requireTz(orgTz);
  return dayjs(iso).tz(orgTz).format("YYYY-MM-DD");
}

export function formatDateTime(iso: string, orgTz: string | undefined): string {
  requireTz(orgTz);
  return dayjs(iso).tz(orgTz).format("MMM D, YYYY h:mm A");
}

export function dateTimeInSalon(iso: string, orgTz: string | undefined) {
  requireTz(orgTz);
  return dayjs(iso).tz(orgTz);
}

export function dateInSalon(date: string, orgTz: string | undefined) {
  requireTz(orgTz);
  return dayjs.tz(`${date}T00:00:00`, orgTz);
}

export function nowInSalon(orgTz: string | undefined) {
  requireTz(orgTz);
  return dayjs().tz(orgTz);
}

export function toIsoFromSalonInput(
  date: string,
  time: string,
  orgTz: string | undefined
): string {
  requireTz(orgTz);
  return dayjs.tz(`${date}T${time}`, orgTz).toISOString();
}

export function minutesFromMidnight(iso: string, orgTz: string | undefined): number {
  requireTz(orgTz);
  const value = dayjs(iso).tz(orgTz);
  return value.hour() * 60 + value.minute();
}

export function businessHoursMin(orgTz: string | undefined) {
  requireTz(orgTz);
  const today = dayjs().tz(orgTz).format("YYYY-MM-DD");
  return dayjs.tz(`${today}T09:00:00`, orgTz);
}

export function businessHoursMax(orgTz: string | undefined) {
  requireTz(orgTz);
  const today = dayjs().tz(orgTz).format("YYYY-MM-DD");
  return dayjs.tz(`${today}T21:00:00`, orgTz);
}
