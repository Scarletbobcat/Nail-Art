create extension if not exists citext;
create extension if not exists pgcrypto;

create table organizations (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    business_phone text,
    timezone text not null default 'America/New_York',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table users (
    id uuid primary key default gen_random_uuid(),
    username citext not null unique,
    email citext,
    password_hash text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table organization_users (
    organization_id uuid not null references organizations(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    role text not null check (role in ('owner', 'admin', 'staff')),
    created_at timestamptz not null default now(),
    primary key (organization_id, user_id)
);

create table organization_settings (
    organization_id uuid primary key references organizations(id) on delete cascade,
    sms_reminders_enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table clients (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name text not null,
    phone_number text,
    sms_consent_status text not null default 'unknown'
        check (sms_consent_status in ('unknown', 'opted_in', 'opted_out')),
    sms_consent_method text
        check (sms_consent_method in ('phone_call', 'in_person', 'imported', 'twilio_stop')),
    sms_consent_at timestamptz,
    sms_blocked_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, organization_id)
);

create unique index clients_org_phone_unique
    on clients (organization_id, phone_number)
    where phone_number is not null and phone_number <> '';

create index clients_org_name_idx on clients (organization_id, lower(name));
create index clients_org_sms_idx on clients (organization_id, sms_consent_status);

create table employees (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name text not null,
    color text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, organization_id)
);

create index employees_org_active_idx on employees (organization_id, active);
create index employees_org_name_idx on employees (organization_id, lower(name));

create table services (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, organization_id)
);

create unique index services_org_name_unique
    on services (organization_id, lower(name));

create index services_org_active_idx on services (organization_id, active);

create table appointments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    client_id uuid,
    employee_id uuid not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    customer_name text not null,
    phone_number text,
    reminder_sent_at timestamptz,
    showed_up boolean not null default false,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (ends_at > starts_at),
    unique (id, organization_id),
    foreign key (client_id, organization_id)
        references clients(id, organization_id)
        on delete set null (client_id),
    foreign key (employee_id, organization_id)
        references employees(id, organization_id)
);

create index appointments_org_starts_at_idx
    on appointments (organization_id, starts_at);

create index appointments_org_employee_starts_at_idx
    on appointments (organization_id, employee_id, starts_at);

create index appointments_org_phone_idx
    on appointments (organization_id, phone_number)
    where phone_number is not null and phone_number <> '';

create index appointments_org_archive_idx
    on appointments (organization_id, archived_at, starts_at);

create table appointment_services (
    organization_id uuid not null references organizations(id) on delete cascade,
    appointment_id uuid not null,
    service_id uuid not null,
    foreign key (appointment_id, organization_id)
        references appointments(id, organization_id)
        on delete cascade,
    foreign key (service_id, organization_id)
        references services(id, organization_id),
    primary key (appointment_id, service_id)
);

create table refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    token text not null unique,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index refresh_tokens_user_idx on refresh_tokens (user_id);
