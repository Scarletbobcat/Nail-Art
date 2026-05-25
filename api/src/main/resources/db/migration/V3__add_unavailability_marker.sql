alter table services
    add column is_unavailability_marker boolean not null default false;

create unique index services_one_unavailability_marker_per_org
    on services (organization_id)
    where is_unavailability_marker;
