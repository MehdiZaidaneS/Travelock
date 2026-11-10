begin;

-- LEFT JOIN keeps accepted friends visible before profile onboarding is finished.
create function public.get_my_friends()
returns table(friendship_id uuid, user_id uuid, profile jsonb)
language plpgsql stable security definer set search_path = ''
as $$
declare actor_id uuid := auth.uid();
begin
  if actor_id is null then
    raise exception 'Sign in before loading friends.' using errcode = '42501';
  end if;
  return query
  select f.friendship_id, other.user_id,
    case when p.user_id is null then null else jsonb_build_object(
      'user_id', p.user_id, 'username', p.username, 'name', p.name,
      'profile_image', p.profile_image) end
  from public.friendships f
  cross join lateral (select case when f.requester_id = actor_id
    then f.recipient_id else f.requester_id end as user_id) other
  left join public.profiles p on p.user_id = other.user_id
  where f.status = 'accepted' and (f.requester_id = actor_id or f.recipient_id = actor_id)
  order by lower(p.username) nulls last, other.user_id;
end;
$$;

-- Delete only this accepted relationship, never a later request between the pair.
-- Pending/rejected/nonexistent/other people's records are left unchanged.
create function public.remove_friend(relationship_id uuid)
returns boolean
language plpgsql security definer set search_path = ''
as $$
declare actor_id uuid := auth.uid(); removed_id uuid;
begin
  if actor_id is null then
    raise exception 'Sign in before removing a friend.' using errcode = '42501';
  end if;
  if relationship_id is null then
    raise exception 'A friendship ID is required.' using errcode = '22023';
  end if;
  delete from public.friendships f
  where f.friendship_id = relationship_id and f.status = 'accepted'
    and (f.requester_id = actor_id or f.recipient_id = actor_id)
  returning f.friendship_id into removed_id;
  return removed_id is not null;
end;
$$;
revoke all on function public.get_my_friends() from public, anon, authenticated;
revoke all on function public.remove_friend(uuid) from public, anon, authenticated;
grant execute on function public.get_my_friends() to authenticated;
grant execute on function public.remove_friend(uuid) to authenticated;

-- RLS and uncached photo authorization use the current relationship. Previously
-- authorized CDN/device caches are not purged. Public posts stay signed-in readable.
commit;
