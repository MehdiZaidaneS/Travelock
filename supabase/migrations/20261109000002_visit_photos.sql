-- First step for the combined QR + visit photo + 50-metre proximity flow.
-- Run once in Supabase SQL Editor; this adds storage, not unlock validation.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'visit-photos',
  'visit-photos',
  false,
  10485760,
  array['image/jpeg', 'image/png', 'image/webp']
);

-- Object paths start with the signed-in user's ID, for example:
-- <user-id>/<visit-id>/photo.jpg. No public read access is added.
create policy "Users can upload their own visit photos"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'visit-photos'
  and (storage.foldername(name))[1] = (select auth.uid()::text)
  and owner_id = (select auth.uid()::text)
);

create policy "Users can read their own visit photos"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'visit-photos'
  and (storage.foldername(name))[1] = (select auth.uid()::text)
  and owner_id = (select auth.uid()::text)
);

-- No client UPDATE/DELETE policy is added here, so this migration does not
-- introduce a way to replace/remove photos after they are used as visit proof.
-- Existing project-wide storage policies must also be reviewed before rollout.
-- MIME restrictions alone do not verify image contents or where it was taken.
commit;
