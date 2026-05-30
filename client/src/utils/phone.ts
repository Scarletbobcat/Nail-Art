// Phone-input formatting shared with the appointment search field: accept only
// partial US numbers and auto-insert a dash after the area code and prefix.
const PHONE_INPUT_REGEX = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;

/**
 * Returns the next phone value (with auto-dash) if the keystroke is valid, or
 * `null` to reject it — mirroring the appointment search input's behavior.
 */
export function formatPhoneInput(next: string, prev: string): string | null {
  if (!PHONE_INPUT_REGEX.test(next)) {
    return null;
  }
  if ((next.length === 3 && prev.length === 2) || (next.length === 7 && prev.length === 6)) {
    return next + "-";
  }
  return next;
}
