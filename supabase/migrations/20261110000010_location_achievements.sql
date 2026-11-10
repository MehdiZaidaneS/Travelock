-- One permanent achievement per user/location after every destination is verified.
begin;

create table public.user_achievements (
  achievement_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  location_id uuid not null references public.locations (location_id),
  level integer not null default 1 check (level = 1),
  unlocked_at timestamptz not null default clock_timestamp(),
  unique (user_id, location_id)
);
alter table public.user_achievements enable row level security;
revoke all on public.user_achievements from public, anon, authenticated;
grant select on public.user_achievements to authenticated;
create policy "Users can read their own achievements" on public.user_achievements
for select to authenticated using (user_id = (select auth.uid()));

-- Internal helper. Keep VOLATILE: after waiting for the lock, the aggregate
-- needs a fresh READ COMMITTED snapshot of other completed visits.
create function public.award_location_if_complete(target_user_id uuid, target_location_id uuid)
returns void language plpgsql volatile security definer set search_path = ''
as $$
declare total bigint; verified bigint;
begin
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
    'travelock_location_award:' || target_user_id::text || ':' || target_location_id::text, 0));
  select count(*), count(*) filter (where exists (
    select 1 from public.user_unlocks as u
    join public.visit_attempts as v on v.user_id = u.user_id and v.destination_id = u.destination_id
    where u.user_id = target_user_id and u.destination_id = d.destination_id
      and v.completed_at is not null and v.cancelled_at is null and v.photo_path is not null
  )) into total, verified
  from public.destinations as d where d.location_id = target_location_id;
  if total > 0 and verified = total then
    insert into public.user_achievements(user_id, location_id)
    values(target_user_id, target_location_id)
    on conflict(user_id, location_id) do nothing;
  end if;
end;
$$;

create function public.award_location_after_visit()
returns trigger language plpgsql volatile security definer set search_path = ''
as $$
declare visited_location uuid;
begin
  if new.completed_at is not null and new.cancelled_at is null and new.photo_path is not null then
    select location_id into visited_location from public.destinations where destination_id = new.destination_id;
    if visited_location is not null then
      perform public.award_location_if_complete(new.user_id, visited_location);
    end if;
  end if;
  return new;
end;
$$;
-- complete_visit inserts the unlock before updating the completed visit row.
create trigger location_achievement_after_visit after insert or update of completed_at
on public.visit_attempts for each row execute function public.award_location_after_visit();

revoke all on function public.award_location_if_complete(uuid, uuid) from public, anon, authenticated;
revoke all on function public.award_location_after_visit() from public, anon, authenticated;

-- The client can request a recheck, never choose an award, location or another user.
create function public.refresh_my_achievements()
returns void language plpgsql volatile security definer set search_path = ''
as $$
declare actor_id uuid := auth.uid(); candidate record;
begin
  if actor_id is null then
    raise exception 'Sign in before refreshing achievements.' using errcode = '42501';
  end if;
  for candidate in
    select distinct d.location_id from public.visit_attempts as v
    join public.destinations as d on d.destination_id = v.destination_id
    where v.user_id = actor_id and v.completed_at is not null and v.cancelled_at is null
    order by d.location_id
  loop
    perform public.award_location_if_complete(actor_id, candidate.location_id);
  end loop;
end;
$$;

create function public.get_my_location_progress()
returns table (
  location_id uuid, location_name text, total_destinations bigint, verified_destinations bigint,
  is_complete boolean, is_earned boolean, achievement_id uuid, unlocked_at timestamptz,
  achievement_name text, achievement_description text
)
language plpgsql stable security invoker set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in before loading location progress.' using errcode = '42501';
  end if;
  return query
  with counts as (
    select d.location_id, count(*) as total,
      count(*) filter (where exists (
        select 1 from public.user_unlocks as u
        join public.visit_attempts as v on v.user_id = u.user_id and v.destination_id = u.destination_id
        where u.user_id = (select auth.uid()) and u.destination_id = d.destination_id
          and v.completed_at is not null and v.cancelled_at is null and v.photo_path is not null
      )) as verified
    from public.destinations as d group by d.location_id
  )
  select l.location_id, l.name, coalesce(c.total, 0), coalesce(c.verified, 0),
    coalesce(c.total > 0 and c.verified = c.total, false), a.achievement_id is not null,
    a.achievement_id, a.unlocked_at, 'Explore ' || l.name, 'Verify every destination in ' || l.name || '.'
  from public.locations as l
  left join counts as c on c.location_id = l.location_id
  left join public.user_achievements as a on a.location_id = l.location_id and a.user_id = (select auth.uid())
  order by l.name, l.location_id;
end;
$$;
revoke all on function public.refresh_my_achievements() from public, anon, authenticated;
revoke all on function public.get_my_location_progress() from public, anon, authenticated;
grant execute on function public.refresh_my_achievements() to authenticated;
grant execute on function public.get_my_location_progress() to authenticated;

-- Award existing eligible verified progress now, without inventing old visit dates.
do $$ declare candidate record; begin
  for candidate in
    select distinct v.user_id, d.location_id from public.visit_attempts as v
    join public.destinations as d on d.destination_id = v.destination_id
    where v.completed_at is not null and v.cancelled_at is null
    order by v.user_id, d.location_id
  loop
    perform public.award_location_if_complete(candidate.user_id, candidate.location_id);
  end loop;
end; $$;

commit;
