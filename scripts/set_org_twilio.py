import argparse
import getpass
import os
import sys

import psycopg
from dotenv import load_dotenv

load_dotenv()


class SetOrgTwilioError(Exception):
    pass


def postgres_url(cli_url: str | None) -> str:
    url = cli_url or os.getenv("POSTGRES_URL")
    if not url:
        raise SetOrgTwilioError("POSTGRES_URL is required or pass --db-url")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def encryption_key() -> str:
    key = os.getenv("APP_ENCRYPTION_KEY")
    if not key:
        raise SetOrgTwilioError("APP_ENCRYPTION_KEY is required (must match the API's key)")
    return key


def read_auth_token() -> str:
    """Read the secret auth token from the environment or an interactive prompt —
    NEVER from a CLI argument, which would land in shell history."""
    token = os.getenv("TWILIO_AUTH_TOKEN")
    if not token and sys.stdin.isatty():
        token = getpass.getpass("Twilio auth token: ")
    if not token:
        raise SetOrgTwilioError(
            "Twilio auth token required via TWILIO_AUTH_TOKEN env or an interactive prompt"
        )
    return token


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Load an organization's Twilio credentials into its DB row, encrypting the "
            "auth token with pgcrypto. Contains no secrets: the token comes from the "
            "TWILIO_AUTH_TOKEN env var or an interactive prompt, never a CLI argument."
        )
    )
    parser.add_argument("--db-url", default=None, help="Postgres URL; defaults to POSTGRES_URL")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--org-id", default=None, help="Organization UUID")
    target.add_argument("--org-name", default=None, help="Organization name")
    parser.add_argument(
        "--account-sid",
        default=os.getenv("TWILIO_ACCOUNT_SID"),
        help="Twilio Account SID (or TWILIO_ACCOUNT_SID env). Not secret.",
    )
    parser.add_argument(
        "--phone-number",
        default=os.getenv("TWILIO_PHONE_NUMBER"),
        help="Twilio sending number (or TWILIO_PHONE_NUMBER env). Not secret.",
    )
    return parser.parse_args()


def resolve_org_id(cur, org_id: str | None, org_name: str | None):
    if org_id:
        cur.execute("select id from organizations where id = %s", (org_id,))
    else:
        cur.execute("select id from organizations where name = %s", (org_name,))
    row = cur.fetchone()
    if row is None:
        raise SetOrgTwilioError("organization not found")
    return row[0]


def set_org_twilio(
    db_url: str,
    *,
    org_id: str | None,
    org_name: str | None,
    account_sid: str | None,
    phone_number: str | None,
    auth_token: str,
    key: str,
) -> str:
    if not account_sid or not phone_number:
        raise SetOrgTwilioError(
            "account SID and phone number are required (set --account-sid/--phone-number "
            "or TWILIO_ACCOUNT_SID/TWILIO_PHONE_NUMBER)"
        )
    conn = psycopg.connect(db_url)
    try:
        with conn.transaction():
            with conn.cursor() as cur:
                resolved = resolve_org_id(cur, org_id, org_name)
                # Pinned pgcrypto contract: pgp_sym_encrypt(token, key), no options string —
                # identical to the Java write/read (U2) and the Python read (U4), so a token
                # written here decrypts byte-identically everywhere. On conflict only the
                # Twilio columns change; sms_reminders_enabled is left intact.
                cur.execute(
                    """
                    insert into organization_settings
                        (organization_id, twilio_account_sid, twilio_phone_number, twilio_auth_token)
                    values (%(org)s, %(sid)s, %(phone)s, pgp_sym_encrypt(%(token)s, %(key)s))
                    on conflict (organization_id) do update set
                        twilio_account_sid = excluded.twilio_account_sid,
                        twilio_phone_number = excluded.twilio_phone_number,
                        twilio_auth_token = excluded.twilio_auth_token,
                        updated_at = now()
                    """,
                    {
                        "org": resolved,
                        "sid": account_sid,
                        "phone": phone_number,
                        "token": auth_token,
                        "key": key,
                    },
                )
                return str(resolved)
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    try:
        db_url = postgres_url(args.db_url)
        key = encryption_key()
        auth_token = read_auth_token()
        org_id = set_org_twilio(
            db_url,
            org_id=args.org_id,
            org_name=args.org_name,
            account_sid=args.account_sid,
            phone_number=args.phone_number,
            auth_token=auth_token,
            key=key,
        )
    except SetOrgTwilioError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    # Never echo the token.
    print(f"Twilio credentials set for organization {org_id} (auth token encrypted).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
