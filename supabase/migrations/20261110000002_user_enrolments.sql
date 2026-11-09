-- Planned destination visits (PBI-11), separate from verified unlocks.
-- Apply once after locations and destinations exist.
begin;

create table public.user_enrolments (
  enrolment_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  destination_id uuid not null references public.destinations (destination_id),
  enrolled_at timestamptz not null default now(),
  constraint user_enrolments_user_destination_key unique (user_id, destination_id)
);

alter table public.user_enrolments enable row level security;
revoke all on public.user_enrolments from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert on public.user_enrolments to authenticated;

create policy "Users can read their own enrolments"
on public.user_enrolments for select to authenticated
using ((select auth.uid()) = user_id);

create policy "Users can create their own enrolments"
on public.user_enrolments for insert to authenticated
with check ((select auth.uid()) = user_id);

-- Enrolling records a plan. It does not create a visit attempt or an unlock,
-- and scanning a QR does not require an enrolment.
commit;
