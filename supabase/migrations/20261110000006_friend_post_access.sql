-- Accepted friends may read posts and their attached private photos.
-- Uploads, post creation, edits and deletes keep their existing rules.
begin;

create policy "Users can read accepted friends' posts"
on public.posts for select to authenticated
using (exists (
  select 1 from public.friendships as f
  where f.status = 'accepted'
    and ((f.requester_id = (select auth.uid()) and f.recipient_id = posts.user_id)
      or (f.recipient_id = (select auth.uid()) and f.requester_id = posts.user_id))
));

-- This boundary also holds if another permissive SELECT policy is added later.
create policy "Posts require authorship or accepted friendship"
on public.posts as restrictive for select to authenticated
using (
  user_id = (select auth.uid()) or exists (
    select 1 from public.friendships as f
    where f.status = 'accepted'
      and ((f.requester_id = (select auth.uid()) and f.recipient_id = posts.user_id)
        or (f.recipient_id = (select auth.uid()) and f.requester_id = posts.user_id))
  )
);

-- Policy helper: the actor always comes from Auth, never from a caller argument.
-- Definer access avoids a Storage/posts policy cycle and lets guest Storage
-- reads return false without needing SELECT permission on the posts table.
create function public.can_read_post_photo(object_name text, object_owner_id text)
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
        join public.friendships as f on f.status = 'accepted'
          and ((f.requester_id = auth.uid() and f.recipient_id = p.user_id)
            or (f.recipient_id = auth.uid() and f.requester_id = p.user_id))
        where p.image = object_name
          and p.user_id::text = object_owner_id
          and (storage.foldername(object_name))[2] = p.post_id::text
      )
    ), false
  );
$$;

revoke all on function public.can_read_post_photo(text, text) from public, anon, authenticated;
-- The restrictive Storage guard targets both roles. Guests always get false.
grant execute on function public.can_read_post_photo(text, text) to anon, authenticated;

create policy "Users can read accepted friends' attached post photos"
on storage.objects for select to authenticated
using (bucket_id = 'post-photos' and public.can_read_post_photo(name, owner_id));

-- Keep this historical policy name to avoid renaming a managed Storage policy.
-- Owners can still read their own uploads; friends need an exact post attachment.
alter policy "Post photo reads require ownership" on storage.objects
using (bucket_id <> 'post-photos' or public.can_read_post_photo(name, owner_id));

-- visit-photos and other buckets are unaffected. The bucket stays private.
commit;
