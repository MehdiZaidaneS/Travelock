-- Recreates the profiles table and policies already configured in the shared project.
-- Do not run this migration against a database where profiles already exists.
begin;

-- A profile uses the same UUID as its Supabase Auth account.
create table public.profiles (
  user_id uuid not null,
  username text not null,
  name text not null,
  profile_image text not null default ''::text,
  created_at timestamp with time zone not null default now(),
  constraint profiles_pkey primary key (user_id),
  constraint profiles_username_key unique (username),
  constraint profiles_user_id_fkey foreign key (user_id)
    references auth.users (id) on delete cascade
);

alter table public.profiles enable row level security;

-- Guests have no profile access. Signed-in users may read, create and edit
-- profiles only as allowed by the ownership policies below; no DELETE grant.
revoke all on table public.profiles from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert, update on table public.profiles to authenticated;

create policy "Users can read their own profile"
on public.profiles
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "Users can create their own profile"
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "Users can update their own profile"
on public.profiles
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- This does not create Auth users or profiles automatically. The application
-- must insert the signed-in user's profile when signup/onboarding is integrated.
commit;
