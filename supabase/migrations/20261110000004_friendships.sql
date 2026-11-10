-- Friend requests and accepted friendships share one record.
-- This does not yet grant access to friends' profiles, posts or photos.
begin;

create table public.friendships (
  friendship_id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users (id) on delete cascade,
  recipient_id uuid not null references auth.users (id) on delete cascade,
  status text not null default 'pending',
  created_at timestamptz not null default clock_timestamp(),
  responded_at timestamptz,
  constraint friendships_no_self_request check (requester_id <> recipient_id),
  constraint friendships_status_check check (status in ('pending', 'accepted', 'rejected')),
  constraint friendships_response_check check (
    (status = 'pending' and responded_at is null)
    or (status in ('accepted', 'rejected') and responded_at is not null)
  )
);

-- A reversed request counts as the same active relationship. Rejected requests
-- remain as history, and a later request can create a fresh pending record.
create unique index friendships_active_pair_key on public.friendships
  (least(requester_id, recipient_id), greatest(requester_id, recipient_id))
  where status in ('pending', 'accepted');
create index friendships_requester_status_idx on public.friendships (requester_id, status);
create index friendships_recipient_status_idx on public.friendships (recipient_id, status);

alter table public.friendships enable row level security;
revoke all on public.friendships from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select on public.friendships to authenticated;

create policy "Users can read relationships they participate in"
on public.friendships for select to authenticated
using ((select auth.uid()) = requester_id or (select auth.uid()) = recipient_id);

create function public.send_friend_request(target_user_id uuid)
returns public.friendships
language plpgsql security definer set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  saved public.friendships;
begin
  if actor_id is null then
    raise exception 'Sign in before sending a friend request.' using errcode = '42501';
  end if;
  if target_user_id is null or target_user_id = actor_id then
    raise exception 'Choose another user for the friend request.' using errcode = '22023';
  end if;
  insert into public.friendships (requester_id, recipient_id)
  values (actor_id, target_user_id)
  on conflict do nothing returning * into saved;

  if saved.friendship_id is null then
    select f.* into saved from public.friendships as f
    where least(f.requester_id, f.recipient_id) = least(actor_id, target_user_id)
      and greatest(f.requester_id, f.recipient_id) = greatest(actor_id, target_user_id)
      and f.status in ('pending', 'accepted');
    if not found then
      raise exception 'The request changed. Please try again.' using errcode = '40001';
    end if;
  end if;
  -- Sending again never accepts an incoming request automatically.
  return saved;
end;
$$;

create function public.respond_to_friend_request(request_id uuid, accept_request boolean)
returns public.friendships
language plpgsql security definer set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  saved public.friendships;
  next_status text;
begin
  if actor_id is null then
    raise exception 'Sign in before responding to a friend request.' using errcode = '42501';
  end if;
  if accept_request is null then
    raise exception 'Choose whether to accept or reject the request.' using errcode = '22023';
  end if;
  select f.* into saved from public.friendships as f
  where f.friendship_id = request_id and f.recipient_id = actor_id for update;
  if not found then
    raise exception 'Incoming friend request not found.' using errcode = '22023';
  end if;
  next_status := case when accept_request then 'accepted' else 'rejected' end;
  if saved.status = next_status then return saved; end if;
  if saved.status <> 'pending' then
    raise exception 'This request has already been answered.' using errcode = '22023';
  end if;
  update public.friendships set status = next_status, responded_at = clock_timestamp()
  where friendship_id = request_id returning * into saved;
  return saved;
end;
$$;

revoke all on function public.send_friend_request(uuid) from public, anon, authenticated;
revoke all on function public.respond_to_friend_request(uuid, boolean) from public, anon, authenticated;
grant execute on function public.send_friend_request(uuid) to authenticated;
grant execute on function public.respond_to_friend_request(uuid, boolean) to authenticated;

commit;
