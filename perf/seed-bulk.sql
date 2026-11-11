-- NFR section 4 volume: 100 000 runs and 1 000 000 history rows (10 events per run), plus an open task for every waiting run.
-- Shaped like the Object Mother's runs (Mother.doc: id, requester, counterparty, notional), dated over the last 90 days,
-- statuses spread 60% COMPLETED, 30% WAITING, 10% FAILED. Idempotent: ids are derived from the sequence number, so a second
-- run inserts nothing. Applied by `make seed-bulk` (psql in the compose container) and by MigrationTest (A6).
insert into instances (id, definition, version, status, current_step, end_status, vars, created_at, updated_at)
select md5('bulk-run-' || g)::uuid, 'confirmation-approval', 1,
       case when g % 10 < 6 then 'COMPLETED' when g % 10 < 9 then 'WAITING' else 'FAILED' end,
       case when g % 10 < 6 then null else 'legal-review' end,
       case when g % 10 < 6 then 'approved' end,
       jsonb_build_object('bulk', true, 'doc', jsonb_build_object('id', 'D-' || (1000 + g), 'requester', 'l.jones',
                                                    'counterparty', (array['ACME','Globex','Initech','Umbrella'])[1 + g % 4],
                                                    'notional', 1000000 + (g % 500) * 100000)),
       now() - (g % 90) * interval '1 day' - (g % 1440) * interval '1 minute',
       now() - (g % 90) * interval '1 day'
from generate_series(1, 100000) g
on conflict (id) do nothing;

insert into events (instance_id, type, step, outcome, actor, payload, at)
select r.id, (array['instance-started','step-entered','step-completed','step-entered','step-completed',
                    'step-entered','step-completed','step-entered','step-completed','step-entered'])[e],
       'legal-review', case when e % 2 = 1 then 'OK' end, 'bulk-seed', '{}'::jsonb,
       r.created_at + e * interval '1 minute'
from instances r, generate_series(1, 10) e
where r.vars ->> 'bulk' = 'true'
  and not exists (select 1 from events x where x.instance_id = r.id);

insert into user_tasks (id, instance_id, step, assignee_type, assignee, scope, status, form)
select md5('bulk-task-' || r.id)::uuid, r.id, 'legal-review', 'ROLE', 'legal', '{}'::jsonb, 'OPEN', null
from instances r
where r.status = 'WAITING' and r.vars ->> 'bulk' = 'true'
on conflict (id) do nothing;
