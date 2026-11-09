-- Apply after 20261110000000_cancel_visit.sql.
-- Files are removed through the Storage API, never by deleting metadata in SQL.
begin;

-- Keep the historical policy name: hosted policy grants may not cover RENAME.
alter policy "Visit photos cannot be deleted by clients" on storage.objects
using (
  bucket_id <> 'visit-photos'
  or (
    owner_id = (select auth.uid()::text)
    and (storage.foldername(name))[1] = (select auth.uid()::text)
    and exists (
      select 1 from public.visit_attempts as v
      where v.visit_id::text = (storage.foldername(name))[2]
        and v.user_id = (select auth.uid())
        and v.cancelled_at is not null
        and v.completed_at is null
    )
  )
);

create policy "Users can delete photos of their cancelled visits"
on storage.objects for delete to authenticated
using (
  bucket_id = 'visit-photos'
  and owner_id = (select auth.uid()::text)
  and (storage.foldername(name))[1] = (select auth.uid()::text)
  and exists (
    select 1 from public.visit_attempts as v
    where v.visit_id::text = (storage.foldername(name))[2]
      and v.user_id = (select auth.uid())
      and v.cancelled_at is not null
      and v.completed_at is null
  )
);

commit;
