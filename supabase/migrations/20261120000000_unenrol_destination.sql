-- Remove a plan without deleting visits, unlocks or achievements.
-- Apply after 20261110000002_user_enrolments.sql.
begin;

grant delete on public.user_enrolments to authenticated;

create policy "Users can delete their own enrolments"
on public.user_enrolments for delete to authenticated
using ((select auth.uid()) = user_id);

commit;
