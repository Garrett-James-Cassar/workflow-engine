create table instances (
    id           uuid primary key,
    definition   text        not null,
    version      int         not null,
    mode         text        not null default 'live', -- 'preview' arrives with the studio
    status       text        not null,
    current_step text,
    end_status   text,
    vars         jsonb       not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create table user_tasks (
    id            uuid primary key,
    instance_id   uuid        not null references instances (id),
    step          text        not null,
    assignee_type text        not null, -- USER | ROLE
    assignee      text        not null,
    scope         jsonb       not null,
    status        text        not null, -- OPEN | DONE
    outcome       text,
    form          jsonb,
    decided_by    text,
    decided_at    timestamptz,
    created_at    timestamptz not null default now()
);
create index user_tasks_open on user_tasks (instance_id) where status = 'OPEN';

create table waits (
    id                uuid primary key,
    instance_id       uuid        not null references instances (id),
    step              text        not null,
    event             text        not null,
    correlation_key   text,
    correlation_value text,
    expires_at        timestamptz,
    status            text        not null, -- OPEN | DONE
    created_at        timestamptz not null default now()
);
create index waits_open_event on waits (event) where status = 'OPEN';
create index waits_open_expiry on waits (expires_at) where status = 'OPEN' and expires_at is not null;

-- Append-only history: the source of truth for timelines, audit and (later) rewind.
create table events (
    id          bigserial primary key,
    instance_id uuid        not null references instances (id),
    type        text        not null,
    step        text,
    outcome     text,
    actor       text,
    payload     jsonb       not null,
    at          timestamptz not null
);
create index events_instance on events (instance_id, id);

create function events_reject_change() returns trigger language plpgsql as $$
begin
    raise exception 'events are append-only';
end
$$;
create trigger events_append_only
    before update or delete on events
    for each row execute function events_reject_change();
