-- Enforce unique organization names. The operator scripts (bootstrap_organization_owner.py,
-- set_org_twilio.py) and the admin create-salon pre-check all resolve an org by name; a
-- duplicate name makes those lookups ambiguous. This is the DB backstop behind the
-- application-level dup-name check in AdminProvisioningService. Case-insensitive so
-- "Salon" and "salon" can't both exist. Fails loudly if duplicate names already exist.
create unique index organizations_name_unique on organizations (lower(name));
