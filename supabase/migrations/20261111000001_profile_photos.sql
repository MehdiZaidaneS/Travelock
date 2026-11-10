begin;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('profile-photos','profile-photos',false,10485760,array['image/jpeg']);

-- Owners can preview uploads. Other signed-in users can read only the exact
-- picture currently attached to a profile, matching basic profile discovery.
create function public.can_read_profile_photo(object_name text, object_owner_id text)
returns boolean language sql stable security definer set search_path = ''
as $$
  select coalesce(auth.uid() is not null
    and (storage.foldername(object_name))[1] = object_owner_id
    and (object_owner_id = auth.uid()::text or exists (
      select 1 from public.profiles p
      where p.user_id::text = object_owner_id and p.profile_image = object_name
    )),false);
$$;
revoke all on function public.can_read_profile_photo(text,text) from public,anon,authenticated;
grant execute on function public.can_read_profile_photo(text,text) to anon,authenticated;

create policy "Owners can upload profile photos" on storage.objects for insert to authenticated
with check(bucket_id='profile-photos' and owner_id=(select auth.uid()::text)
  and (storage.foldername(name))[1]=(select auth.uid()::text));
create policy "Signed-in users can read current profile photos" on storage.objects for select to authenticated
using(bucket_id='profile-photos' and public.can_read_profile_photo(name,owner_id));

-- Restrictive guards also apply if another permissive Storage policy is added.
create policy "Profile photo uploads require ownership" on storage.objects as restrictive for insert to anon,authenticated
with check(bucket_id<>'profile-photos' or (owner_id=(select auth.uid()::text)
  and (storage.foldername(name))[1]=(select auth.uid()::text)));
create policy "Profile photo reads require current attachment" on storage.objects as restrictive for select to anon,authenticated
using(bucket_id<>'profile-photos' or public.can_read_profile_photo(name,owner_id));
create policy "Profile photos cannot be replaced by clients" on storage.objects as restrictive for update to anon,authenticated
using(bucket_id<>'profile-photos') with check(bucket_id<>'profile-photos');
create policy "Profile photos cannot be deleted by clients" on storage.objects as restrictive for delete to anon,authenticated
using(bucket_id<>'profile-photos');

create function public.validate_profile_photo()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  -- Existing image values are kept until changed. No backfill or URL rewrite.
  if tg_op='UPDATE' and new.user_id=old.user_id
    and new.profile_image is not distinct from old.profile_image then return new; end if;
  if new.profile_image='' then return new; end if;
  if not exists (
    select 1 from storage.objects o where o.bucket_id='profile-photos'
      and o.name=new.profile_image and o.owner_id=new.user_id::text
      and (storage.foldername(o.name))[1]=new.user_id::text
      and o.metadata->>'mimetype'='image/jpeg'
      and case when o.metadata->>'size' ~ '^[0-9]+$'
        then (o.metadata->>'size')::numeric between 1 and 10485760 else false end
  ) then
    raise exception 'Upload your own JPEG profile photo before saving its path.' using errcode='23514';
  end if;
  return new;
end;
$$;
revoke all on function public.validate_profile_photo() from public,anon,authenticated;
create trigger profile_photo_validation before insert or update of profile_image,user_id on public.profiles
for each row execute function public.validate_profile_photo();

-- Changes only the image, so a photo save does not overwrite name edits.
create function public.set_my_profile_photo(photo_path text)
returns public.profiles language plpgsql security invoker set search_path = ''
as $$
declare saved public.profiles;
begin
  if auth.uid() is null then raise exception 'Sign in before saving a profile photo.' using errcode='42501'; end if;
  if photo_path is null then raise exception 'Supply a photo path, or an empty string to clear it.' using errcode='22023'; end if;
  update public.profiles set profile_image=photo_path where user_id=(select auth.uid()) returning * into saved;
  if saved.user_id is null then raise exception 'Create your profile before saving its picture.' using errcode='22023'; end if;
  return saved;
end;
$$;
revoke all on function public.set_my_profile_photo(text) from public,anon,authenticated;
grant execute on function public.set_my_profile_photo(text) to authenticated;

commit;
