-- Optional sharing after a completed visit. Public means signed-in users only.
begin;

alter table public.posts
  add column visibility text not null default 'friends'
    constraint posts_visibility_check check (visibility in ('friends', 'public')),
  add column visit_id uuid references public.visit_attempts (visit_id);

-- Existing posts keep their privacy and have no invented visit history.
-- All future client inserts must reference a completed visit for this destination.
create policy "New posts require a completed own visit"
on public.posts as restrictive for insert to authenticated
with check (
  user_id = (select auth.uid())
  and visit_id is not null
  and exists (
    select 1 from public.visit_attempts as v
    where v.visit_id = posts.visit_id
      and v.user_id = (select auth.uid())
      and v.destination_id = posts.destination_id
      and v.completed_at is not null
      and v.cancelled_at is null
      and v.photo_path is not null
  )
);

create policy "Signed-in users can read public posts"
on public.posts for select to authenticated
using (visibility = 'public');

-- Keep the historical name; this boundary now also allows public visibility.
alter policy "Posts require authorship or accepted friendship" on public.posts
using (
  visibility = 'public'
  or user_id = (select auth.uid())
  or exists (
    select 1 from public.friendships as f
    where f.status = 'accepted'
      and ((f.requester_id = (select auth.uid()) and f.recipient_id = posts.user_id)
        or (f.recipient_id = (select auth.uid()) and f.requester_id = posts.user_id))
  )
);

-- Read only the exact shared post attachment, never its private visit evidence
-- or other files in the same folder. The Storage bucket remains private.
create or replace function public.can_read_post_photo(object_name text, object_owner_id text)
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce(
    auth.uid() is not null
    and (storage.foldername(object_name))[1] = object_owner_id
    and (
      object_owner_id = auth.uid()::text
      or exists (
        select 1 from public.posts as p
        where p.image = object_name
          and p.user_id::text = object_owner_id
          and (storage.foldername(object_name))[2] = p.post_id::text
          and (
            p.visibility = 'public'
            or exists (
              select 1 from public.friendships as f
              where f.status = 'accepted'
                and ((f.requester_id = auth.uid() and f.recipient_id = p.user_id)
                  or (f.recipient_id = auth.uid() and f.requester_id = p.user_id))
            )
          )
      )
    ), false
  );
$$;

-- Existing function grants and Storage guards still apply. No guest table
-- grants, public bucket, automatic posts or client post updates are introduced.
commit;
