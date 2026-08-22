begin;

-- The user explicitly discarded the empty legacy business dataset after the
-- canonical import was verified. Auth users and canonical profiles remain.
drop schema if exists legacy cascade;

create schema if not exists extensions;
alter extension pg_trgm set schema extensions;

alter function public.prevent_audit_mutation() set search_path = '';

create index if not exists achievements_definition_idx
  on public.achievements(achievement_id);
create index if not exists ai_reports_user_idx
  on public.ai_reports(user_id, created_at desc);
create index if not exists comments_parent_idx
  on public.comments(parent_id) where parent_id is not null;
create index if not exists conversations_listing_idx
  on public.conversations(listing_id) where listing_id is not null;
create index if not exists posts_author_idx
  on public.posts(author_id, created_at desc);

-- Reads already include administrators. Writes use role-checking RPCs, so the
-- broad FOR ALL policies were redundant and caused duplicate SELECT policies.
drop policy if exists announcements_admin on public.announcements;
drop policy if exists releases_admin on public.app_releases;

commit;
