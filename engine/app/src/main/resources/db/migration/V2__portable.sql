-- Config documents that arrived through POST /import, keyed by kind and natural key (PORTABLE.md).
create table config_docs (
    doc_kind   text        not null,
    doc_key    text        not null,
    doc        text        not null,
    updated_at timestamp with time zone not null,
    primary key (doc_kind, doc_key)
);

-- One row per import, with the counts it returned.
create table import_audit (
    id        bigserial primary key,
    at        timestamp with time zone not null,
    actor     text        not null,
    created   int         not null,
    updated   int         not null,
    skipped   int         not null,
    conflicts int         not null
);
