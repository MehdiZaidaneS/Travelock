-- Recreates the user_unlocks table and policy already configured in the shared project.
-- Do not run this migration against a database where user_unlocks already exists.
begin;

create table public.user_unlocks (
  unlock_id uuid not null default gen_random_uuid(),
  user_id uuid not null,
  destination_id uuid not null,
  unlocked_at timestamp with time zone not null default now(),
  constraint user_unlocks_pkey primary key (unlock_id),
  constraint user_unlocks_user_destination_key unique (user_id, destination_id),
  constraint user_unlocks_destination_id_fkey foreign key (destination_id)
    references public.destinations (destination_id),
  constraint user_unlocks_user_id_fkey foreign key (user_id)
    references auth.users (id) on delete cascade
);

alter table public.user_unlocks enable row level security;

-- Signed-in users can read unlocks. Awarding them requires trusted backend code.
revoke all on table public.user_unlocks from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select on table public.user_unlocks to authenticated;

create policy "Users can read their own unlocks"
on public.user_unlocks
for select
to authenticated
using ((select auth.uid()) = user_id);

-- No client INSERT, UPDATE or DELETE grants or policies. QR verification and
-- the backend operation that awards unlocks are separate work.
commit;
