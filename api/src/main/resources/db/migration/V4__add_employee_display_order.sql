alter table employees
    add column display_order integer not null default 0;

with ranked_employees as (
    select
        id,
        row_number() over (
            partition by organization_id
            order by created_at, id
        ) - 1 as display_order
    from employees
)
update employees
set display_order = ranked_employees.display_order
from ranked_employees
where employees.id = ranked_employees.id;

alter table employees
    add constraint employees_display_order_nonnegative check (display_order >= 0);

create index employees_org_display_order_idx
    on employees (organization_id, display_order, name, id);
