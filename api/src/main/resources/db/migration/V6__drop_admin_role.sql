-- Drop the vestigial 'admin' org role. In practice salons have only owners and staff;
-- 'admin' was never used and muddled the org-role model with the future platform super-user
-- (which is a User-level capability, not an org role). Two-tier model: owner / staff.

-- Fail loud if any membership still uses 'admin' so the role's meaning never changes silently.
do $$
begin
    if exists (select 1 from organization_users where role = 'admin') then
        raise exception 'Cannot drop admin role: % organization_users row(s) still use it',
            (select count(*) from organization_users where role = 'admin');
    end if;
end $$;

alter table organization_users
    drop constraint organization_users_role_check;

alter table organization_users
    add constraint organization_users_role_check check (role in ('owner', 'staff'));
