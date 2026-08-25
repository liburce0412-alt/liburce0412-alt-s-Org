create or replace function private.sync_post_comment_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  affected_post uuid;
begin
  affected_post := case when tg_op = 'DELETE' then old.post_id else new.post_id end;

  update public.posts post
  set comment_count = (
    select count(*)::integer
    from public.comments comment
    where comment.post_id = affected_post
      and comment.deleted_at is null
  )
  where post.id = affected_post;

  if tg_op = 'UPDATE' and old.post_id is distinct from new.post_id then
    update public.posts post
    set comment_count = (
      select count(*)::integer
      from public.comments comment
      where comment.post_id = old.post_id
        and comment.deleted_at is null
    )
    where post.id = old.post_id;
  end if;

  return case when tg_op = 'DELETE' then old else new end;
end;
$$;

revoke all on function private.sync_post_comment_count() from public;

drop trigger if exists comments_sync_post_count on public.comments;
create trigger comments_sync_post_count
after insert or delete or update of deleted_at, post_id on public.comments
for each row execute function private.sync_post_comment_count();

update public.posts post
set comment_count = (
  select count(*)::integer
  from public.comments comment
  where comment.post_id = post.id
    and comment.deleted_at is null
);
