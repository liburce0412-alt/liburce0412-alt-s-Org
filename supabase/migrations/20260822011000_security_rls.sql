begin;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.is_staff(check_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.profiles p where p.id=check_user and p.role in ('moderator','admin','super_admin') and not p.is_blocked) $$;

create or replace function private.is_admin(check_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.profiles p where p.id=check_user and p.role in ('admin','super_admin') and not p.is_blocked) $$;

create or replace function private.is_super_admin(check_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.profiles p where p.id=check_user and p.role='super_admin' and not p.is_blocked) $$;

create or replace function private.is_conversation_member(check_conversation uuid, check_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.conversation_members m where m.conversation_id=check_conversation and m.user_id=check_user) $$;

create or replace function public.touch_updated_at() returns trigger language plpgsql set search_path=public as $$
begin new.updated_at=now(); return new; end $$;

create or replace function public.create_profile_for_new_user() returns trigger language plpgsql security definer set search_path=public as $$
begin
  insert into public.profiles(id,display_name,handle)
  values(new.id,coalesce(nullif(new.raw_user_meta_data->>'display_name',''),split_part(new.email,'@',1),'CampusAI 用户'),nullif(new.raw_user_meta_data->>'handle',''))
  on conflict(id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.create_profile_for_new_user();

do $$ declare name text; begin
  foreach name in array array['profiles','time_entries','time_goals','course_schedules','posts','comments','listings','conversations','orders','announcements'] loop
    execute format('drop trigger if exists %I_touch_updated_at on public.%I',name,name);
    execute format('create trigger %I_touch_updated_at before update on public.%I for each row execute procedure public.touch_updated_at()',name,name);
  end loop;
end $$;

alter table public.profiles enable row level security;
alter table public.time_entries enable row level security;
alter table public.time_goals enable row level security;
alter table public.focus_sessions enable row level security;
alter table public.course_schedules enable row level security;
alter table public.achievement_definitions enable row level security;
alter table public.achievements enable row level security;
alter table public.posts enable row level security;
alter table public.comments enable row level security;
alter table public.post_likes enable row level security;
alter table public.post_bookmarks enable row level security;
alter table public.listings enable row level security;
alter table public.favorites enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.orders enable row level security;
alter table public.announcements enable row level security;
alter table public.reports enable row level security;
alter table public.app_releases enable row level security;
alter table public.audit_logs enable row level security;
alter table public.ai_reports enable row level security;
alter table public.ai_usage_daily enable row level security;

-- Data API table privileges are explicit; RLS then determines which rows are
-- visible. Do not rely on project-level default privileges.
revoke all on public.profiles,public.time_entries,public.time_goals,public.focus_sessions,public.course_schedules,
  public.achievement_definitions,public.achievements,public.posts,public.comments,public.post_likes,public.post_bookmarks,
  public.listings,public.favorites,public.conversations,public.conversation_members,public.messages,public.orders,
  public.announcements,public.reports,public.app_releases,public.audit_logs,public.ai_reports,public.ai_usage_daily
from anon,authenticated;
grant select on public.profiles,public.achievement_definitions,public.achievements,public.posts,public.comments,
  public.post_likes,public.post_bookmarks,public.listings,public.favorites,public.conversations,public.conversation_members,
  public.messages,public.orders,public.announcements,public.reports,public.app_releases,public.audit_logs,public.ai_reports,
  public.ai_usage_daily to authenticated;
grant select,insert,update,delete on public.time_entries,public.time_goals,public.focus_sessions,public.course_schedules to authenticated;
grant insert,update on public.posts,public.comments,public.listings,public.announcements,public.reports,public.app_releases to authenticated;
grant insert on public.messages to authenticated;

drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select to authenticated using (not is_blocked or id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists profiles_self_update on public.profiles;
create policy profiles_self_update on public.profiles for update to authenticated using(id=(select auth.uid()) and not is_blocked) with check(id=(select auth.uid()));
revoke update on public.profiles from authenticated;
grant update(display_name,handle,avatar_path,cover_path,bio,settings,updated_at) on public.profiles to authenticated;

drop policy if exists time_entries_owner on public.time_entries;
create policy time_entries_owner on public.time_entries for all to authenticated using(user_id=(select auth.uid()) or (select private.is_admin())) with check(user_id=(select auth.uid()) or (select private.is_admin()));
drop policy if exists time_goals_owner on public.time_goals;
create policy time_goals_owner on public.time_goals for all to authenticated using(user_id=(select auth.uid()) or (select private.is_admin())) with check(user_id=(select auth.uid()) or (select private.is_admin()));
drop policy if exists focus_sessions_owner on public.focus_sessions;
create policy focus_sessions_owner on public.focus_sessions for all to authenticated using(user_id=(select auth.uid()) or (select private.is_admin())) with check(user_id=(select auth.uid()) or (select private.is_admin()));
drop policy if exists course_schedules_owner on public.course_schedules;
create policy course_schedules_owner on public.course_schedules for all to authenticated using(user_id=(select auth.uid()) or (select private.is_admin())) with check(user_id=(select auth.uid()) or (select private.is_admin()));

drop policy if exists achievement_definitions_read on public.achievement_definitions;
create policy achievement_definitions_read on public.achievement_definitions for select to authenticated using(true);
drop policy if exists achievements_read on public.achievements;
create policy achievements_read on public.achievements for select to authenticated using(user_id=(select auth.uid()) or (select private.is_admin()));

drop policy if exists posts_read on public.posts;
create policy posts_read on public.posts for select to authenticated using((moderation_status='approved' and deleted_at is null) or author_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists posts_insert on public.posts;
create policy posts_insert on public.posts for insert to authenticated with check(author_id=(select auth.uid()));
drop policy if exists posts_author_update on public.posts;
create policy posts_author_update on public.posts for update to authenticated using(author_id=(select auth.uid()) or (select private.is_staff())) with check(author_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists comments_read on public.comments;
create policy comments_read on public.comments for select to authenticated using((moderation_status='approved' and deleted_at is null) or author_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists comments_insert on public.comments;
create policy comments_insert on public.comments for insert to authenticated with check(author_id=(select auth.uid()));
drop policy if exists comments_update on public.comments;
create policy comments_update on public.comments for update to authenticated using(author_id=(select auth.uid()) or (select private.is_staff())) with check(author_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists post_likes_read on public.post_likes;
create policy post_likes_read on public.post_likes for select to authenticated using(true);
drop policy if exists post_bookmarks_owner on public.post_bookmarks;
create policy post_bookmarks_owner on public.post_bookmarks for select to authenticated using(user_id=(select auth.uid()) or (select private.is_admin()));

drop policy if exists listings_read on public.listings;
create policy listings_read on public.listings for select to authenticated using((moderation_status='approved' and status not in ('draft','removed')) or seller_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists listings_insert on public.listings;
create policy listings_insert on public.listings for insert to authenticated with check(seller_id=(select auth.uid()));
drop policy if exists listings_update on public.listings;
create policy listings_update on public.listings for update to authenticated using(seller_id=(select auth.uid()) or (select private.is_staff())) with check(seller_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists favorites_owner on public.favorites;
create policy favorites_owner on public.favorites for select to authenticated using(user_id=(select auth.uid()) or (select private.is_admin()));

drop policy if exists conversations_members on public.conversations;
create policy conversations_members on public.conversations for select to authenticated using((select private.is_admin()) or (select private.is_conversation_member(id)));
drop policy if exists conversation_members_read on public.conversation_members;
create policy conversation_members_read on public.conversation_members for select to authenticated using((select private.is_admin()) or (select private.is_conversation_member(conversation_id)));
drop policy if exists messages_members_read on public.messages;
create policy messages_members_read on public.messages for select to authenticated using((select private.is_admin()) or (select private.is_conversation_member(messages.conversation_id)));
drop policy if exists messages_members_insert on public.messages;
create policy messages_members_insert on public.messages for insert to authenticated with check(sender_id=(select auth.uid()) and (select private.is_conversation_member(messages.conversation_id)));

drop policy if exists orders_parties_read on public.orders;
create policy orders_parties_read on public.orders for select to authenticated using(buyer_id=(select auth.uid()) or seller_id=(select auth.uid()) or (select private.is_admin()));
drop policy if exists announcements_read on public.announcements;
create policy announcements_read on public.announcements for select to authenticated using((status='published' and coalesce(publish_at,created_at)<=now()) or (select private.is_admin()));
drop policy if exists announcements_admin on public.announcements;
create policy announcements_admin on public.announcements for all to authenticated using((select private.is_admin())) with check((select private.is_admin()));
drop policy if exists reports_insert on public.reports;
create policy reports_insert on public.reports for insert to authenticated with check(reporter_id=(select auth.uid()));
drop policy if exists reports_read on public.reports;
create policy reports_read on public.reports for select to authenticated using(reporter_id=(select auth.uid()) or (select private.is_staff()));
drop policy if exists reports_admin_update on public.reports;
create policy reports_admin_update on public.reports for update to authenticated using((select private.is_staff())) with check((select private.is_staff()));
drop policy if exists releases_read on public.app_releases;
create policy releases_read on public.app_releases for select to authenticated using(status='published' or (select private.is_admin()));
drop policy if exists releases_admin on public.app_releases;
create policy releases_admin on public.app_releases for all to authenticated using((select private.is_admin())) with check((select private.is_admin()));
drop policy if exists audit_admin_read on public.audit_logs;
create policy audit_admin_read on public.audit_logs for select to authenticated using((select private.is_admin()));
drop policy if exists ai_reports_owner on public.ai_reports;
create policy ai_reports_owner on public.ai_reports for select to authenticated using(user_id=(select auth.uid()) or (select private.is_admin()));
drop policy if exists ai_usage_owner_read on public.ai_usage_daily;
create policy ai_usage_owner_read on public.ai_usage_daily for select to authenticated using(user_id=(select auth.uid()) or (select private.is_admin()));

revoke insert,update,delete on public.audit_logs from anon,authenticated;
revoke insert,update,delete on public.achievements,public.ai_usage_daily from anon,authenticated;

revoke all on function private.is_staff(uuid), private.is_admin(uuid), private.is_super_admin(uuid), private.is_conversation_member(uuid,uuid) from public,anon;
grant execute on function private.is_staff(uuid), private.is_admin(uuid), private.is_super_admin(uuid), private.is_conversation_member(uuid,uuid) to authenticated;
revoke all on function public.create_profile_for_new_user(), public.touch_updated_at() from public,anon,authenticated;

commit;
