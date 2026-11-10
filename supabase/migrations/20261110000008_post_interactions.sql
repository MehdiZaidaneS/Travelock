-- Likes and comments follow the parent post's existing visibility rules.
begin;

create table public.post_likes (
  post_id uuid not null references public.posts (post_id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default clock_timestamp(),
  primary key (post_id, user_id)
);

create table public.comments (
  comment_id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (post_id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  -- Match Kotlin's whitespace set, independent of the database locale.
  content text not null constraint comments_content_not_blank
    check (content ~ U&'[^\0009-\000D\001C-\0020\00A0\1680\2000-\200A\2028-\2029\202F\205F\3000]'),
  created_at timestamptz not null default clock_timestamp()
);
create index comments_post_order_idx on public.comments (post_id, created_at desc, comment_id desc);

alter table public.post_likes enable row level security;
alter table public.comments enable row level security;
revoke all on public.post_likes, public.comments from public, anon, authenticated;
grant select, delete on public.post_likes to authenticated;
grant insert (post_id, user_id) on public.post_likes to authenticated;
grant select on public.comments to authenticated;
grant insert (post_id, user_id, content) on public.comments to authenticated;
-- IDs and timestamps are supplied by the database, not by client insert payloads.

create policy "Visible post likes can be read" on public.post_likes
for select to authenticated using (
  (select auth.uid()) is not null
  and exists (select 1 from public.posts as p where p.post_id = post_likes.post_id)
);
create policy "Users can like visible posts as themselves" on public.post_likes
for insert to authenticated with check (
  user_id = (select auth.uid())
  and exists (select 1 from public.posts as p where p.post_id = post_likes.post_id)
);
create policy "Users can remove their own visible post likes" on public.post_likes
for delete to authenticated using (
  user_id = (select auth.uid())
  and exists (select 1 from public.posts as p where p.post_id = post_likes.post_id)
);
create policy "Visible post comments can be read" on public.comments
for select to authenticated using (
  (select auth.uid()) is not null
  and exists (select 1 from public.posts as p where p.post_id = comments.post_id)
);
create policy "Users can comment on visible posts as themselves" on public.comments
for insert to authenticated with check (
  user_id = (select auth.uid())
  and exists (select 1 from public.posts as p where p.post_id = comments.post_id)
);

-- Invoker functions keep the caller's table privileges and RLS policies active.
create function public.set_post_like(target_post_id uuid, should_like boolean)
returns boolean language plpgsql security invoker set search_path = ''
as $$
declare actor_id uuid := auth.uid();
begin
  if actor_id is null then
    raise exception 'Sign in before liking a post.' using errcode = '42501';
  end if;
  if should_like is null then
    raise exception 'Choose whether to like the post.' using errcode = '22023';
  end if;
  if not exists (select 1 from public.posts where post_id = target_post_id) then
    raise exception 'Post is not available.' using errcode = '42501';
  end if;
  if should_like then
    insert into public.post_likes (post_id, user_id) values (target_post_id, actor_id)
    on conflict (post_id, user_id) do nothing;
  else
    delete from public.post_likes where post_id = target_post_id and user_id = actor_id;
  end if;
  return should_like;
end;
$$;

create function public.add_post_comment(target_post_id uuid, comment_content text)
returns public.comments language plpgsql security invoker set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  normalized_content text := regexp_replace(comment_content,
    U&'^[\0009-\000D\001C-\0020\00A0\1680\2000-\200A\2028-\2029\202F\205F\3000]+|[\0009-\000D\001C-\0020\00A0\1680\2000-\200A\2028-\2029\202F\205F\3000]+$', '', 'g');
  saved public.comments;
begin
  if actor_id is null then
    raise exception 'Sign in before commenting.' using errcode = '42501';
  end if;
  if not exists (select 1 from public.posts where post_id = target_post_id) then
    raise exception 'Post is not available.' using errcode = '42501';
  end if;
  if normalized_content is null or normalized_content = '' then
    raise exception 'Comment cannot be blank.' using errcode = '22023';
  end if;
  insert into public.comments (post_id, user_id, content)
  values (target_post_id, actor_id, normalized_content) returning * into saved;
  return saved;
end;
$$;

create function public.get_post_interaction_summary(target_post_id uuid)
returns table (like_count bigint, comment_count bigint, is_liked_by_me boolean)
language plpgsql stable security invoker set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in before loading post interactions.' using errcode = '42501';
  end if;
  if not exists (select 1 from public.posts where post_id = target_post_id) then
    raise exception 'Post is not available.' using errcode = '42501';
  end if;
  return query select
    (select count(*) from public.post_likes as l where l.post_id = target_post_id),
    (select count(*) from public.comments as c where c.post_id = target_post_id),
    exists (select 1 from public.post_likes as l where l.post_id = target_post_id and l.user_id = auth.uid());
end;
$$;

revoke all on function public.set_post_like(uuid, boolean) from public, anon, authenticated;
revoke all on function public.add_post_comment(uuid, text) from public, anon, authenticated;
revoke all on function public.get_post_interaction_summary(uuid) from public, anon, authenticated;
grant execute on function public.set_post_like(uuid, boolean) to authenticated;
grant execute on function public.add_post_comment(uuid, text) to authenticated;
grant execute on function public.get_post_interaction_summary(uuid) to authenticated;

commit;
