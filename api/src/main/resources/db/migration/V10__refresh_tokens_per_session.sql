alter table refresh_tokens rename column token to token_hash;

alter table refresh_tokens
    add column organization_id uuid references organizations(id) on delete cascade,
    add column last_used_at timestamptz;

update refresh_tokens rt
set organization_id = (
    select ou.organization_id
    from organization_users ou
    where ou.user_id = rt.user_id
    order by ou.created_at
    limit 1
)
where rt.organization_id is null
  and exists (
      select 1
      from organization_users ou
      where ou.user_id = rt.user_id
  );

update refresh_tokens
set token_hash = encode(digest(token_hash, 'sha256'), 'hex')
where token_hash !~ '^[0-9a-f]{64}$';
