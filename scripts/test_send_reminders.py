from datetime import datetime

from send_reminders import build_message


WHEN = datetime(2026, 4, 10, 10, 0)


def test_build_message_uses_per_org_salon_identity() -> None:
    message_a = build_message(WHEN, "Salon Alpha", "330-111-1111")
    message_b = build_message(WHEN, "Salon Bravo", "330-222-2222")

    # Each salon's clients hear only that salon's name and phone -- never the other's.
    assert "Salon Alpha" in message_a and "330-111-1111" in message_a
    assert "Salon Bravo" not in message_a and "330-222-2222" not in message_a
    assert "Salon Bravo" in message_b and "330-222-2222" in message_b
    assert "Salon Alpha" not in message_b and "330-111-1111" not in message_b


def test_build_message_has_no_hardcoded_salon() -> None:
    message = build_message(WHEN, "Salon Alpha", "330-111-1111")

    assert "Nail Art & Spa LLC." not in message
    assert "330-758-6633" not in message


def test_build_message_tolerates_missing_business_phone() -> None:
    message = build_message(WHEN, "Salon Alpha", None)

    assert "Salon Alpha" in message
    assert "Please call the salon if you need to reschedule or cancel." in message
