-- Basic profile discovery for signed-in users. Full profile table access
-- remains governed by the existing ownership policies.
begin;

create function public.search_profiles(search_query text)
returns table (user_id uuid, username text, name text, profile_image text)
language plpgsql stable security definer set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  query_text text := pg_catalog.lower(pg_catalog.btrim(search_query));
begin
  if actor_id is null then
    raise exception 'Sign in before searching for users.' using errcode = '42501';
  end if;
  -- An empty search should not list everyone. Search text is treated literally,
  -- so characters such as % and _ do not turn into wildcard searches.
  if query_text is null or query_text = '' then return; end if;
  return query
    select p.user_id, p.username, p.name, p.profile_image
    from public.profiles as p
    where p.user_id <> actor_id
      and (pg_catalog.strpos(pg_catalog.lower(p.username), query_text) > 0
        or pg_catalog.strpos(pg_catalog.lower(p.name), query_text) > 0)
    order by pg_catalog.lower(p.username), p.user_id
    limit 20;
end;
$$;

create function public.get_profile_summary(profile_user_id uuid)
returns table (user_id uuid, username text, name text, profile_image text)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in before viewing a profile.' using errcode = '42501';
  end if;
  return query
    select p.user_id, p.username, p.name, p.profile_image
    from public.profiles as p where p.user_id = profile_user_id;
end;
$$;

revoke all on function public.search_profiles(text) from public, anon, authenticated;
revoke all on function public.get_profile_summary(uuid) from public, anon, authenticated;
grant execute on function public.search_profiles(text) to authenticated;
grant execute on function public.get_profile_summary(uuid) to authenticated;

-- These functions do not expose email, Auth metadata, posts or visit history.
-- Profile updates still go through the existing own-profile policies.
commit;
