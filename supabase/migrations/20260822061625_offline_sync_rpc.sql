begin;

create or replace function public.sync_time_entry(
  client_entry uuid,
  entry_title text,
  entry_category text,
  entry_description text,
  entry_starts_at timestamptz,
  entry_ends_at timestamptz,
  client_version integer,
  client_updated_at timestamptz
)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  current_entry public.time_entries%rowtype;
  is_conflict boolean := false;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode = '28000'; end if;
  if char_length(btrim(coalesce(entry_title, ''))) not between 1 and 120 or entry_ends_at < entry_starts_at then
    raise exception 'invalid_time_entry' using errcode = '22023';
  end if;

  select * into current_entry from public.time_entries
  where user_id = auth.uid() and client_id = client_entry for update;

  if not found then
    insert into public.time_entries(user_id, client_id, title, category, description, starts_at, ends_at, version, sync_state, updated_at)
    values(auth.uid(), client_entry, btrim(entry_title), coalesce(nullif(btrim(entry_category), ''), '其他'), coalesce(entry_description, ''), entry_starts_at, entry_ends_at, greatest(client_version, 1), 'synced', coalesce(client_updated_at, now()))
    returning * into current_entry;
  elsif current_entry.deleted_at is not null and client_version > current_entry.version then
    update public.time_entries
    set title = btrim(entry_title), category = coalesce(nullif(btrim(entry_category), ''), '其他'),
        description = coalesce(entry_description, ''), starts_at = entry_starts_at, ends_at = entry_ends_at,
        version = client_version, sync_state = 'synced', deleted_at = null
    where id = current_entry.id returning * into current_entry;
  elsif current_entry.deleted_at is not null then
    is_conflict := true;
  elsif client_version >= current_entry.version or client_updated_at >= current_entry.updated_at then
    update public.time_entries
    set title = btrim(entry_title), category = coalesce(nullif(btrim(entry_category), ''), '其他'),
        description = coalesce(entry_description, ''), starts_at = entry_starts_at, ends_at = entry_ends_at,
        version = greatest(current_entry.version + 1, client_version), sync_state = 'synced', deleted_at = null,
        updated_at = greatest(coalesce(client_updated_at, now()), now())
    where id = current_entry.id returning * into current_entry;
  else
    is_conflict := true;
  end if;

  return jsonb_build_object('conflict', is_conflict, 'entry', to_jsonb(current_entry));
end;
$$;

create or replace function public.sync_course_schedule(
  client_course uuid,
  course_name text,
  course_weekday smallint,
  course_start_minute smallint,
  course_end_minute smallint,
  course_location text,
  course_teacher text,
  course_weeks text,
  course_source_hash text,
  client_version integer,
  client_updated_at timestamptz
)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  current_course public.course_schedules%rowtype;
  is_conflict boolean := false;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode = '28000'; end if;
  if char_length(btrim(coalesce(course_name, ''))) not between 1 and 160
    or course_weekday not between 1 and 7
    or course_start_minute not between 0 and 1439
    or course_end_minute not between 1 and 1440
    or course_end_minute <= course_start_minute
    or course_source_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_course_schedule' using errcode = '22023';
  end if;

  select * into current_course from public.course_schedules
  where user_id = auth.uid() and (client_id = client_course or source_hash = course_source_hash)
  order by case when client_id = client_course then 0 else 1 end
  limit 1 for update;

  if not found then
    insert into public.course_schedules(user_id, client_id, name, weekday, start_minute, end_minute, location, teacher, weeks, source_hash, version, updated_at)
    values(auth.uid(), client_course, btrim(course_name), course_weekday, course_start_minute, course_end_minute, coalesce(course_location, ''), coalesce(course_teacher, ''), coalesce(course_weeks, ''), course_source_hash, greatest(client_version, 1), coalesce(client_updated_at, now()))
    returning * into current_course;
  elsif current_course.deleted_at is not null then
    is_conflict := true;
  elsif client_version >= current_course.version or client_updated_at >= current_course.updated_at then
    update public.course_schedules
    set name = btrim(course_name), weekday = course_weekday, start_minute = course_start_minute,
        end_minute = course_end_minute, location = coalesce(course_location, ''), teacher = coalesce(course_teacher, ''),
        weeks = coalesce(course_weeks, ''), source_hash = course_source_hash,
        version = greatest(current_course.version + 1, client_version), deleted_at = null,
        updated_at = greatest(coalesce(client_updated_at, now()), now())
    where id = current_course.id returning * into current_course;
  else
    is_conflict := true;
  end if;

  return jsonb_build_object('conflict', is_conflict, 'entry', to_jsonb(current_course));
end;
$$;

create or replace function public.delete_course_schedule(target_course uuid, expected_version integer)
returns integer
language plpgsql security definer set search_path = ''
as $$
declare next_version integer;
begin
  update public.course_schedules
  set deleted_at = now(), version = version + 1
  where id = target_course and user_id = auth.uid() and version = expected_version and deleted_at is null
  returning version into next_version;
  if next_version is null then raise exception 'course_conflict_or_missing'; end if;
  return next_version;
end;
$$;

revoke insert, update, delete on public.time_entries, public.course_schedules from authenticated;

revoke all on function public.sync_time_entry(uuid, text, text, text, timestamptz, timestamptz, integer, timestamptz),
  public.sync_course_schedule(uuid, text, smallint, smallint, smallint, text, text, text, text, integer, timestamptz),
  public.delete_course_schedule(uuid, integer)
from public, anon;

grant execute on function public.sync_time_entry(uuid, text, text, text, timestamptz, timestamptz, integer, timestamptz) to authenticated;
grant execute on function public.sync_course_schedule(uuid, text, smallint, smallint, smallint, text, text, text, text, integer, timestamptz) to authenticated;
grant execute on function public.delete_course_schedule(uuid, integer) to authenticated;

commit;
