-- PBI-12: validate a scanned code and record the signed-in user's unlock.
-- Apply after the user_unlocks migration; the existing tables are not recreated.
begin;

create function public.unlock_destination(scanned_qr_code text)
returns public.user_unlocks
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  matched_destination_id uuid;
  saved_unlock public.user_unlocks;
begin
  if current_user_id is null then
    raise exception 'You must sign in before unlocking a destination.'
      using errcode = '42501';
  end if;

  if scanned_qr_code is null or pg_catalog.btrim(scanned_qr_code) = '' then
    raise exception 'Invalid QR code.' using errcode = '22023';
  end if;

  -- Codes currently live in destinations. Reject ambiguous codes rather than
  -- choosing a destination when multiple rows contain the same code.
  begin
    select d.destination_id into strict matched_destination_id
    from public.destinations as d
    where d.qr_code = scanned_qr_code;
  exception
    when no_data_found then
      raise exception 'Invalid QR code.' using errcode = '22023';
    when too_many_rows then
      raise exception 'QR code matches multiple destinations.'
        using errcode = '22023';
  end;

  -- The session supplies ownership; callers cannot award another user's unlock.
  -- Keep the original timestamp when the same destination is scanned again.
  insert into public.user_unlocks (user_id, destination_id)
  values (current_user_id, matched_destination_id)
  on conflict on constraint user_unlocks_user_destination_key do nothing
  returning * into saved_unlock;

  if saved_unlock.unlock_id is null then
    select u.* into strict saved_unlock
    from public.user_unlocks as u
    where u.user_id = current_user_id
      and u.destination_id = matched_destination_id;
  end if;

  return saved_unlock;
end;
$$;

-- Only this operation can write on behalf of an authenticated caller.
-- Direct client INSERT, UPDATE and DELETE permissions remain revoked.
revoke all on function public.unlock_destination(text)
  from public, anon, authenticated;
grant execute on function public.unlock_destination(text) to authenticated;

comment on function public.unlock_destination(text) is
  'Validates a destination QR code and returns the session user''s unlock. Does not verify physical presence; destination QR values are currently public.';

commit;
