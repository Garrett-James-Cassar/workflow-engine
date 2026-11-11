-- NFR P1: the task list pages open tasks oldest first (created_at, id); without this every page sorts every open task.
create index user_tasks_open_created on user_tasks (created_at, id) where status = 'OPEN';
