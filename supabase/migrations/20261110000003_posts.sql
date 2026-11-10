-- First posts step: author-only access until friendship rules are implemented.
-- Upload to post-photos/<user-id>/<post-id>/<filename> before inserting the post.
begin;

create table public.posts (
  post_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  destination_id uuid not null references public.destinations (destination_id),
  image text not null,
  image_description text not null default '',
  created_at timestamptz not null default now()
);

alter table public.posts enable row level security;
revoke all on public.posts from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert on public.posts to authenticated;

create policy "Users can read their own posts"
on public.posts for select to authenticated
using ((select auth.uid()) = user_id);

-- Store an object path in image. A URL or another user's object is not accepted.
create policy "Users can create posts with their own uploaded photo"
on public.posts for insert to authenticated
with check (
  user_id = (select auth.uid())
  and (storage.foldername(image))[1] = (select auth.uid()::text)
  and (storage.foldername(image))[2] = post_id::text
  and exists (
    select 1 from storage.objects as o
    where o.bucket_id = 'post-photos'
      and o.name = posts.image
      and o.owner_id = (select auth.uid()::text)
      and o.metadata ->> 'mimetype' in ('image/jpeg', 'image/png', 'image/webp')
      and case when (o.metadata ->> 'size') ~ '^[0-9]+$'
        then (o.metadata ->> 'size')::numeric between 1 and 10485760
        else false end
  )
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('post-photos', 'post-photos', false, 10485760,
  array['image/jpeg', 'image/png', 'image/webp']);

create policy "Users can upload their own post photos"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'post-photos'
  and owner_id = (select auth.uid()::text)
  and (storage.foldername(name))[1] = (select auth.uid()::text)
);

create policy "Users can read their own post photos"
on storage.objects for select to authenticated
using (
  bucket_id = 'post-photos'
  and owner_id = (select auth.uid()::text)
  and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- Keep other buckets unchanged, including existing visit-photo policies.
-- These guards also apply when a project has a broad permissive Storage policy.
create policy "Post photo reads require ownership"
on storage.objects as restrictive for select to anon, authenticated
using (bucket_id <> 'post-photos' or (
  owner_id = (select auth.uid()::text)
  and (storage.foldername(name))[1] = (select auth.uid()::text)
));

create policy "Post photo uploads require ownership"
on storage.objects as restrictive for insert to anon, authenticated
with check (bucket_id <> 'post-photos' or (
  owner_id = (select auth.uid()::text)
  and (storage.foldername(name))[1] = (select auth.uid()::text)
));

create policy "Post photos cannot be replaced by clients"
on storage.objects as restrictive for update to anon, authenticated
using (bucket_id <> 'post-photos') with check (bucket_id <> 'post-photos');

create policy "Post photos cannot be deleted by clients"
on storage.objects as restrictive for delete to anon, authenticated
using (bucket_id <> 'post-photos');

commit;
