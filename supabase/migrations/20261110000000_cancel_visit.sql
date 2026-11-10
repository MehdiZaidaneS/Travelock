-- Apply after 20261109000003_visit_attempts.sql.
-- Cancellation keeps the attempt and any private uploaded photos for later cleanup.
begin;

alter table public.visit_attempts add column cancelled_at timestamptz;
alter table public.visit_attempts add constraint visit_attempts_cancel_check
  check (cancelled_at is null or completed_at is null);

create function public.cancel_visit(attempt_id uuid)
returns public.visit_attempts
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  visit public.visit_attempts;
begin
  if current_user_id is null then
    raise exception 'You must sign in before cancelling a visit.' using errcode = '42501';
  end if;
  select v.* into visit from public.visit_attempts as v
  where v.visit_id = attempt_id and v.user_id = current_user_id for update;
  if not found then
    raise exception 'Visit attempt not found.' using errcode = '22023';
  end if;
  if visit.completed_at is not null then
    raise exception 'A completed visit cannot be cancelled.' using errcode = '22023';
  end if;
  update public.visit_attempts
  set cancelled_at = coalesce(cancelled_at, clock_timestamp())
  where visit_id = attempt_id
  returning * into visit;
  return visit;
end;
$$;

revoke all on function public.cancel_visit(uuid) from public, anon, authenticated;
grant execute on function public.cancel_visit(uuid) to authenticated;

alter policy "Visit photo uploads require an open attempt" on storage.objects
with check (
  bucket_id <> 'visit-photos'
  or (
    owner_id = (select auth.uid()::text)
    and (storage.foldername(name))[1] = (select auth.uid()::text)
    and exists (
      select 1 from public.visit_attempts as v
      where v.visit_id::text = (storage.foldername(name))[2]
        and v.user_id = (select auth.uid())
        and v.completed_at is null
        and v.cancelled_at is null
    )
  )
);

-- The replacement complete_visit below also locks the attempt before checking
-- cancellation, so a racing cancel/complete request has a single winner.
create or replace function public.complete_visit(
  attempt_id uuid,
  uploaded_photo_path text,
  user_latitude double precision,
  user_longitude double precision
)
returns public.user_unlocks
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  visit public.visit_attempts;
  destination public.destinations;
  saved_unlock public.user_unlocks;
  haversine double precision;
  distance_meters double precision;
begin
  if current_user_id is null then
    raise exception 'You must sign in before completing a visit.' using errcode = '42501';
  end if;

  select v.* into visit from public.visit_attempts as v
  where v.visit_id = attempt_id and v.user_id = current_user_id
  for update;
  if not found then
    raise exception 'Visit attempt not found.' using errcode = '22023';
  end if;
  if visit.cancelled_at is not null then
    raise exception 'This visit has been cancelled.' using errcode = '22023';
  end if;
  if uploaded_photo_path is null or pg_catalog.btrim(uploaded_photo_path) = '' then
    raise exception 'A visit photo is required.' using errcode = '22023';
  end if;
  if visit.photo_path is not null and visit.photo_path <> uploaded_photo_path then
    raise exception 'This visit was completed with a different photo.' using errcode = '22023';
  end if;

  -- Look up an uploaded object, not a client-supplied URL or "photo taken" flag.
  -- Paths must be <user-id>/<visit-id>/<filename> and uploads must follow the scan.
  perform 1 from storage.objects as o
  where o.bucket_id = 'visit-photos'
    and o.name = uploaded_photo_path
    and o.owner_id = current_user_id::text
    and pg_catalog.split_part(o.name, '/', 1) = current_user_id::text
    and pg_catalog.split_part(o.name, '/', 2) = visit.visit_id::text
    and o.created_at >= visit.started_at
    and o.metadata ->> 'mimetype' in ('image/jpeg', 'image/png', 'image/webp')
    and case
      when (o.metadata ->> 'size') ~ '^[0-9]+$'
      then (o.metadata ->> 'size')::numeric between 1 and 10485760
      else false
    end
  for share;
  if not found then
    raise exception 'Upload a photo for this visit before completing it.' using errcode = '22023';
  end if;

  if user_latitude is null or user_longitude is null
    or user_latitude not between -90.0 and 90.0
    or user_longitude not between -180.0 and 180.0 then
    raise exception 'Valid location coordinates are required.' using errcode = '22023';
  end if;

  select d.* into strict destination from public.destinations as d
  where d.destination_id = visit.destination_id for share;
  if destination.latitude not between -90.0 and 90.0
    or destination.longitude not between -180.0 and 180.0 then
    raise exception 'Destination coordinates are invalid.' using errcode = '22023';
  end if;

  -- Match the Android calculator's approximate surface distance in metres.
  haversine :=
    pg_catalog.sin(pg_catalog.radians(user_latitude - destination.latitude) / 2) ^ 2
    + pg_catalog.cos(pg_catalog.radians(destination.latitude))
    * pg_catalog.cos(pg_catalog.radians(user_latitude))
    * pg_catalog.sin(pg_catalog.radians(user_longitude - destination.longitude) / 2) ^ 2;
  distance_meters := 2 * 6371008.8
    * pg_catalog.asin(pg_catalog.sqrt(least(1.0, greatest(0.0, haversine))));
  if distance_meters > 50.0 then
    raise exception 'You must be within 50 metres of the destination.' using errcode = '22023';
  end if;

  insert into public.user_unlocks (user_id, destination_id)
  values (current_user_id, visit.destination_id)
  on conflict on constraint user_unlocks_user_destination_key do nothing
  returning * into saved_unlock;
  if saved_unlock.unlock_id is null then
    select u.* into strict saved_unlock from public.user_unlocks as u
    where u.user_id = current_user_id and u.destination_id = visit.destination_id;
  end if;

  update public.visit_attempts
  set photo_path = uploaded_photo_path,
      completed_at = coalesce(completed_at, clock_timestamp())
  where visit_id = visit.visit_id;
  return saved_unlock;
end;
$$;


commit;
