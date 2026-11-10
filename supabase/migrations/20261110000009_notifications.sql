-- In-app notifications only. Trigger payloads use the real source event rows.
begin;

create table public.notifications (
  notification_id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users (id) on delete cascade,
  receiver_id uuid not null references auth.users (id) on delete cascade,
  type text not null check (type in ('like', 'comment', 'friend_request')),
  message text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default clock_timestamp(),
  post_id uuid references public.posts (post_id) on delete cascade,
  comment_id uuid references public.comments (comment_id) on delete cascade,
  friendship_id uuid references public.friendships (friendship_id) on delete cascade,
  constraint notifications_not_self check (sender_id <> receiver_id),
  constraint notifications_target_check check (
    (type = 'like' and post_id is not null and comment_id is null and friendship_id is null)
    or (type = 'comment' and post_id is not null and comment_id is not null and friendship_id is null)
    or (type = 'friend_request' and post_id is null and comment_id is null and friendship_id is not null)
  )
);
create unique index notifications_like_key on public.notifications (post_id, sender_id) where type = 'like';
create unique index notifications_comment_key on public.notifications (comment_id) where type = 'comment';
create unique index notifications_request_key on public.notifications (friendship_id) where type = 'friend_request';
create index notifications_receiver_order_idx on public.notifications (receiver_id, created_at desc, notification_id desc);

alter table public.notifications enable row level security;
revoke all on public.notifications from public, anon, authenticated;
grant select on public.notifications to authenticated;
grant update (is_read) on public.notifications to authenticated;

create policy "Receivers can read available notifications" on public.notifications
for select to authenticated using (
  receiver_id = (select auth.uid()) and (
    (type in ('like', 'comment') and exists (
      select 1 from public.posts as p where p.post_id = notifications.post_id and p.user_id = (select auth.uid())
    )) or (type = 'friend_request' and exists (
      select 1 from public.friendships as f where f.friendship_id = notifications.friendship_id
        and f.recipient_id = (select auth.uid()) and f.requester_id = notifications.sender_id and f.status = 'pending'
    ))
  )
);
create policy "Receivers can mark available notifications read" on public.notifications
for update to authenticated using (
  receiver_id = (select auth.uid()) and (
    (type in ('like', 'comment') and exists (
      select 1 from public.posts as p where p.post_id = notifications.post_id and p.user_id = (select auth.uid())
    )) or (type = 'friend_request' and exists (
      select 1 from public.friendships as f where f.friendship_id = notifications.friendship_id
        and f.recipient_id = (select auth.uid()) and f.requester_id = notifications.sender_id and f.status = 'pending'
    ))
  )
) with check (receiver_id = (select auth.uid()));

create function public.notify_post_like()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare recipient uuid;
begin
  if tg_op = 'DELETE' then
    delete from public.notifications where type = 'like' and post_id = old.post_id and sender_id = old.user_id;
    return old;
  end if;
  select user_id into recipient from public.posts where post_id = new.post_id;
  if recipient is not null and recipient <> new.user_id then
    insert into public.notifications(sender_id, receiver_id, type, message, post_id)
    values(new.user_id, recipient, 'like', 'Liked your post.', new.post_id)
    on conflict do nothing;
  end if;
  return new;
end;
$$;
create trigger post_like_notification after insert or delete on public.post_likes
for each row execute function public.notify_post_like();

create function public.notify_post_comment()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare recipient uuid;
begin
  select user_id into recipient from public.posts where post_id = new.post_id;
  if recipient is not null and recipient <> new.user_id then
    insert into public.notifications(sender_id, receiver_id, type, message, post_id, comment_id)
    values(new.user_id, recipient, 'comment', 'Commented: ' || left(new.content, 120), new.post_id, new.comment_id)
    on conflict do nothing;
  end if;
  return new;
end;
$$;
create trigger post_comment_notification after insert on public.comments
for each row execute function public.notify_post_comment();

create function public.notify_friend_request()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if new.status = 'pending' then
    insert into public.notifications(sender_id, receiver_id, type, message, friendship_id)
    values(new.requester_id, new.recipient_id, 'friend_request', 'Sent you a friend request.', new.friendship_id)
    on conflict do nothing;
  else
    delete from public.notifications where type = 'friend_request' and friendship_id = new.friendship_id;
  end if;
  return new;
end;
$$;
create trigger friend_request_notification after insert or update of status on public.friendships
for each row execute function public.notify_friend_request();

-- Existing pending requests still need attention; older likes/comments are not replayed.
insert into public.notifications(sender_id, receiver_id, type, message, friendship_id, created_at)
select requester_id, recipient_id, 'friend_request', 'Sent you a friend request.', friendship_id, created_at
from public.friendships where status = 'pending'
on conflict do nothing;

revoke all on function public.notify_post_like() from public, anon, authenticated;
revoke all on function public.notify_post_comment() from public, anon, authenticated;
revoke all on function public.notify_friend_request() from public, anon, authenticated;

create function public.get_unread_notification_count()
returns bigint language plpgsql stable security invoker set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in before loading notifications.' using errcode = '42501';
  end if;
  return (select count(*) from public.notifications where not is_read);
end;
$$;
revoke all on function public.get_unread_notification_count() from public, anon, authenticated;
grant execute on function public.get_unread_notification_count() to authenticated;

commit;
