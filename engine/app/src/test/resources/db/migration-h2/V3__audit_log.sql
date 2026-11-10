-- sec.6 (fleet SECURITY.md): one row per state-changing action and per denied request, written in the same
-- transaction as the change. Append-only: the repository only inserts and lists; no route updates or deletes.
create table audit_log (
    id        bigint generated always as identity primary key,
    at        timestamp with time zone not null,
    actor     text not null,
    via       text,
    action    text not null,
    resource  text not null,
    outcome   text not null,
    changes   text not null,
    trace_id  text
);
create index audit_log_at on audit_log (at desc);
create index audit_log_actor on audit_log (actor);

-- The earlier per-import table moves forward into the audit log (one import.apply row each, counts in changes).
insert into audit_log (at, actor, via, action, resource, outcome, changes, trace_id)
select at, actor, null, 'workflows.import', 'workflows', 'ok',
       '["created=' || created || '","updated=' || updated || '","skipped=' || skipped || '","conflicts=' || conflicts || '"]', null
from import_audit;
drop table import_audit;
