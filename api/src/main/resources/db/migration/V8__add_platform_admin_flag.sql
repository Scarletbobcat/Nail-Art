-- Platform super-user capability. A platform admin is a User-level flag, NOT an
-- org role (the vestigial 'admin' org role was dropped in V6). Platform admins
-- have no organization_users membership; their authority comes from this flag.
-- Additive and safe: existing users default to false.
alter table users
    add column is_platform_admin boolean not null default false;
