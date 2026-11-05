-- Run once in the Supabase SQL Editor to support the Android starter example.
-- This is demo data, separate from the future Travelock entity schema.
begin;

create table public.todos (
    id integer generated always as identity primary key,
    name text not null
);

alter table public.todos enable row level security;
revoke all on table public.todos from anon, authenticated;
grant usage on schema public to anon, authenticated;
grant select on table public.todos to anon, authenticated;

create policy "Anyone can read demo todos"
    on public.todos for select
    to anon, authenticated
    using (true);

insert into public.todos (name)
values ('Supabase connection works');

commit;
