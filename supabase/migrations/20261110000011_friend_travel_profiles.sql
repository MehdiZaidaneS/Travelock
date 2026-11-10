-- Share travel summaries with accepted friends without opening private base tables.
begin;

create function public.get_friend_travel_profile(profile_user_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare actor_id uuid := auth.uid(); result jsonb;
begin
  if actor_id is null or profile_user_id is null or not exists (
    select 1 from public.friendships f
    where f.status = 'accepted' and (
      (f.requester_id = actor_id and f.recipient_id = profile_user_id)
      or (f.recipient_id = actor_id and f.requester_id = profile_user_id)
    )
  ) then
    raise exception 'An accepted friendship is required to view travel history.' using errcode = '42501';
  end if;

  select jsonb_build_object(
    'profile', jsonb_build_object('user_id', p.user_id, 'username', p.username,
      'name', p.name, 'profile_image', p.profile_image),
    'achievements', coalesce((
      select jsonb_agg(jsonb_build_object(
        'achievement_id', a.achievement_id, 'user_id', a.user_id,
        'level', a.level, 'unlocked_at', a.unlocked_at, 'location', to_jsonb(l)
      ) order by a.unlocked_at desc, a.achievement_id desc)
      from public.user_achievements a join public.locations l using(location_id)
      where a.user_id = profile_user_id
    ), '[]'::jsonb),
    'unlocks', coalesce((
      select jsonb_agg(jsonb_build_object(
        'unlock_id', u.unlock_id, 'user_id', u.user_id, 'destination_id', u.destination_id,
        'unlocked_at', u.unlocked_at, 'destination', to_jsonb(d)
      ) order by u.unlocked_at desc, u.unlock_id desc)
      from public.user_unlocks u join public.destinations d using(destination_id)
      where u.user_id = profile_user_id
    ), '[]'::jsonb)
  ) into result from public.profiles p where p.user_id = profile_user_id;
  -- A friend may not have completed profile onboarding yet.
  return result;
end;
$$;
revoke all on function public.get_friend_travel_profile(uuid) from public, anon, authenticated;
grant execute on function public.get_friend_travel_profile(uuid) to authenticated;

commit;
