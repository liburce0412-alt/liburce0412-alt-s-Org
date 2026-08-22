begin;

alter table public.app_releases drop constraint if exists app_releases_status_check;
alter table public.app_releases add constraint app_releases_status_check
  check (status in ('draft', 'published', 'archived'));

create or replace function public.admin_set_user_blocked(target_user uuid, blocked boolean)
returns void
language plpgsql security definer set search_path = ''
as $$
declare
  target_role public.app_role;
begin
  if not private.is_admin() then raise exception 'admin_required' using errcode = '42501'; end if;
  if target_user = auth.uid() and blocked then raise exception 'cannot_block_self' using errcode = '22023'; end if;
  select role into target_role from public.profiles where id = target_user for update;
  if target_role is null then raise exception 'profile_not_found'; end if;
  if target_role = 'super_admin' and not private.is_super_admin() then
    raise exception 'super_admin_required' using errcode = '42501';
  end if;
  update public.profiles set is_blocked = blocked where id = target_user;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), case when blocked then 'BLOCK_USER' else 'UNBLOCK_USER' end, 'profile', target_user::text, 'success', jsonb_build_object('blocked', blocked));
end;
$$;

create or replace function public.moderate_content(
  target_type text,
  target_id uuid,
  next_status public.moderation_status
)
returns void
language plpgsql security definer set search_path = ''
as $$
declare
  parent_post uuid;
begin
  if not private.is_staff() then raise exception 'staff_required' using errcode = '42501'; end if;
  if next_status not in ('approved', 'rejected', 'removed') then raise exception 'invalid_moderation_status'; end if;

  case target_type
    when 'post' then
      update public.posts set moderation_status = next_status where id = target_id;
    when 'comment' then
      select post_id into parent_post from public.comments where id = target_id for update;
      if parent_post is null then raise exception 'content_not_found'; end if;
      update public.comments set moderation_status = next_status where id = target_id;
      if parent_post is not null then
        update public.posts post set comment_count = (
          select count(*)::integer from public.comments comment
          where comment.post_id = parent_post and comment.moderation_status = 'approved' and comment.deleted_at is null
        ) where post.id = parent_post;
      end if;
    when 'listing' then
      update public.listings set moderation_status = next_status where id = target_id;
    else raise exception 'unsupported_content_type' using errcode = '22023';
  end case;
  if not found and target_type <> 'comment' then raise exception 'content_not_found'; end if;

  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), 'MODERATE_CONTENT', target_type, target_id::text, 'success', jsonb_build_object('status', next_status));
end;
$$;

create or replace function public.resolve_report(target_report uuid, next_status text, resolution_text text)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not private.is_staff() then raise exception 'staff_required' using errcode = '42501'; end if;
  if next_status not in ('resolved', 'dismissed') then raise exception 'invalid_report_status'; end if;
  if char_length(btrim(coalesce(resolution_text, ''))) not between 1 and 1000 then raise exception 'resolution_required'; end if;
  update public.reports
  set status = next_status, resolution = btrim(resolution_text), assignee_id = auth.uid(), resolved_at = now()
  where id = target_report and status = 'pending';
  if not found then raise exception 'report_not_pending'; end if;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), 'RESOLVE_REPORT', 'report', target_report::text, 'success', jsonb_build_object('status', next_status));
end;
$$;

create or replace function public.admin_create_announcement(announcement_title text, announcement_body text)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare created_id uuid;
begin
  if not private.is_admin() then raise exception 'admin_required' using errcode = '42501'; end if;
  if char_length(btrim(coalesce(announcement_title, ''))) not between 1 and 160
    or char_length(btrim(coalesce(announcement_body, ''))) not between 1 and 10000 then
    raise exception 'invalid_announcement';
  end if;
  insert into public.announcements(title, body, author_id)
  values(btrim(announcement_title), btrim(announcement_body), auth.uid()) returning id into created_id;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result)
  values(auth.uid(), 'CREATE_ANNOUNCEMENT', 'announcement', created_id::text, 'success');
  return created_id;
end;
$$;

create or replace function public.admin_set_announcement_status(target_announcement uuid, next_status text)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not private.is_admin() then raise exception 'admin_required' using errcode = '42501'; end if;
  if next_status not in ('draft', 'published', 'withdrawn') then raise exception 'invalid_announcement_status'; end if;
  update public.announcements
  set status = next_status, publish_at = case when next_status = 'published' then coalesce(publish_at, now()) else publish_at end
  where id = target_announcement;
  if not found then raise exception 'announcement_not_found'; end if;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), 'SET_ANNOUNCEMENT_STATUS', 'announcement', target_announcement::text, 'success', jsonb_build_object('status', next_status));
end;
$$;

create or replace function public.admin_create_release(
  release_version_code integer,
  release_version_name text,
  release_notes text,
  release_apk_url text,
  release_checksum_sha256 text
)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare created_id uuid;
begin
  if not private.is_admin() then raise exception 'admin_required' using errcode = '42501'; end if;
  if release_version_code < 1 or char_length(btrim(coalesce(release_version_name, ''))) not between 1 and 40
    or char_length(btrim(coalesce(release_notes, ''))) not between 1 and 10000
    or release_apk_url !~ '^https://'
    or lower(release_checksum_sha256) !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_release';
  end if;
  insert into public.app_releases(version_code, version_name, notes, apk_url, checksum_sha256, published_by)
  values(release_version_code, btrim(release_version_name), btrim(release_notes), release_apk_url, lower(release_checksum_sha256), auth.uid())
  returning id into created_id;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result)
  values(auth.uid(), 'CREATE_RELEASE', 'app_release', created_id::text, 'success');
  return created_id;
end;
$$;

create or replace function public.admin_set_release_status(target_release uuid, next_status text)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not private.is_admin() then raise exception 'admin_required' using errcode = '42501'; end if;
  if next_status not in ('draft', 'published', 'archived') then raise exception 'invalid_release_status'; end if;
  update public.app_releases
  set status = next_status, published_at = case when next_status = 'published' then coalesce(published_at, now()) else published_at end
  where id = target_release;
  if not found then raise exception 'release_not_found'; end if;
  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), 'SET_RELEASE_STATUS', 'app_release', target_release::text, 'success', jsonb_build_object('status', next_status));
end;
$$;

create or replace function public.withdraw_listing(target_listing uuid)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode = '28000'; end if;
  update public.listings
  set status = 'withdrawn'
  where id = target_listing and seller_id = auth.uid() and status in ('draft', 'active');
  if not found then raise exception 'listing_not_withdrawable'; end if;
end;
$$;

-- Client-owned rows may edit content fields, but moderation, counters, roles and
-- transaction state remain server-owned even if a caller bypasses the UI.
revoke insert, update on public.posts, public.comments, public.listings, public.reports from authenticated;
grant insert(author_id, body, topic, tags, media_paths, is_anonymous) on public.posts to authenticated;
grant update(body, topic, tags, media_paths, is_anonymous) on public.posts to authenticated;
grant insert(seller_id, title, description, price_cents, original_price_cents, category, condition, location, media_paths) on public.listings to authenticated;
grant update(title, description, price_cents, original_price_cents, category, condition, location, media_paths) on public.listings to authenticated;
grant insert(reporter_id, target_type, target_id, reason, details, content_digest) on public.reports to authenticated;

revoke insert, update on public.announcements, public.app_releases from authenticated;
revoke all on function public.admin_set_user_blocked(uuid, boolean),
  public.moderate_content(text, uuid, public.moderation_status),
  public.resolve_report(uuid, text, text),
  public.admin_create_announcement(text, text),
  public.admin_set_announcement_status(uuid, text),
  public.admin_create_release(integer, text, text, text, text),
  public.admin_set_release_status(uuid, text), public.withdraw_listing(uuid)
from public, anon;

grant execute on function public.admin_set_user_blocked(uuid, boolean) to authenticated;
grant execute on function public.moderate_content(text, uuid, public.moderation_status) to authenticated;
grant execute on function public.resolve_report(uuid, text, text) to authenticated;
grant execute on function public.admin_create_announcement(text, text) to authenticated;
grant execute on function public.admin_set_announcement_status(uuid, text) to authenticated;
grant execute on function public.admin_create_release(integer, text, text, text, text) to authenticated;
grant execute on function public.admin_set_release_status(uuid, text) to authenticated;
grant execute on function public.withdraw_listing(uuid) to authenticated;

commit;
