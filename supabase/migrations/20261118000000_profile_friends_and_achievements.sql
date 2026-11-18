-- Allows authenticated users to read other users' profiles (needed for friends list,
-- search, and social feed) and adds the user_achievements table with RLS policies.
begin;

-- Update profiles SELECT policy so any authenticated user can read profiles
drop policy if exists "Users can read their own profile" on public.profiles;
create policy "Authenticated users can read profiles"
on public.profiles
for select
to authenticated
using (true);

-- Create user_achievements table matching com.example.travelock.data.model.UserAchievement
create table if not exists public.user_achievements (
  achievement_id uuid not null default gen_random_uuid (),
  user_id uuid not null,
  location_id uuid not null,
  level integer not null default 1,
  unlocked_at timestamp with time zone not null default now(),
  constraint user_achievements_pkey primary key (achievement_id),
  constraint user_achievements_user_location_key unique (user_id, location_id),
  constraint user_achievements_user_id_fkey foreign key (user_id)
    references auth.users (id) on delete cascade,
  constraint user_achievements_location_id_fkey foreign key (location_id)
    references public.locations (location_id) on delete cascade
);

alter table public.user_achievements enable row level security;

revoke all on table public.user_achievements from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert on table public.user_achievements to authenticated;

create policy "Authenticated users can read achievements"
on public.user_achievements
for select
to authenticated
using (true);

create policy "Users can insert their own achievements"
on public.user_achievements
for insert
to authenticated
with check ((select auth.uid()) = user_id);

commit;
