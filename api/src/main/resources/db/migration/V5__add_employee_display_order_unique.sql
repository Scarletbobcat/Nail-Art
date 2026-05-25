-- Defragment any duplicate display_order within an org (rare; pre-constraint legacy)
with ranked as (
    select
        id,
        row_number() over (
            partition by organization_id
            order by display_order, created_at, id
        ) - 1 as new_display_order
    from employees
)
update employees
set display_order = ranked.new_display_order
from ranked
where employees.id = ranked.id
  and employees.display_order is distinct from ranked.new_display_order;

-- Deferrable so atomic bulk reorder can swap positions within a transaction
-- without tripping the constraint on intermediate states.
alter table employees
    add constraint employees_org_display_order_unique
        unique (organization_id, display_order)
        deferrable initially deferred;
