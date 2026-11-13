begin;

-- Existing profile_image values remain either an owned Storage path or empty.
-- These 12 stable, bundled avatar IDs additionally require no Storage upload.
create or replace function public.validate_profile_photo()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op='UPDATE' and new.user_id=old.user_id
    and new.profile_image is not distinct from old.profile_image then return new; end if;
  if new.profile_image='' then return new; end if;
  if new.profile_image ~ '^preset:moods_(0[1-9]|1[0-2])$' then return new; end if;
  if not exists (
    select 1 from storage.objects o where o.bucket_id='profile-photos'
      and o.name=new.profile_image and o.owner_id=new.user_id::text
      and (storage.foldername(o.name))[1]=new.user_id::text
      and o.metadata->>'mimetype'='image/jpeg'
      and case when o.metadata->>'size' ~ '^[0-9]+$'
        then (o.metadata->>'size')::numeric between 1 and 10485760 else false end
  ) then
    raise exception 'Choose an available avatar or upload your own JPEG profile photo before saving its path.' using errcode='23514';
  end if;
  return new;
end;
$$;
revoke all on function public.validate_profile_photo() from public,anon,authenticated;

commit;
