begin;

create or replace function public.toggle_post_like(target_post uuid) returns jsonb
language plpgsql security definer set search_path=public as $$
declare liked boolean; total integer;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  if not exists(select 1 from posts where id=target_post and deleted_at is null and (moderation_status='approved' or author_id=auth.uid() or private.is_staff())) then
    raise exception 'post_not_available' using errcode='42501';
  end if;
  if exists(select 1 from post_likes where post_id=target_post and user_id=auth.uid()) then
    delete from post_likes where post_id=target_post and user_id=auth.uid(); liked:=false;
  else
    insert into post_likes(post_id,user_id) values(target_post,auth.uid()); liked:=true;
  end if;
  select count(*)::integer into total from post_likes where post_id=target_post;
  update posts set like_count=total where id=target_post;
  return jsonb_build_object('liked',liked,'count',total);
end $$;

create or replace function public.toggle_post_bookmark(target_post uuid) returns boolean
language plpgsql security definer set search_path=public as $$
declare bookmarked boolean;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  if not exists(select 1 from posts where id=target_post and deleted_at is null and (moderation_status='approved' or author_id=auth.uid() or private.is_staff())) then
    raise exception 'post_not_available' using errcode='42501';
  end if;
  if exists(select 1 from post_bookmarks where post_id=target_post and user_id=auth.uid()) then
    delete from post_bookmarks where post_id=target_post and user_id=auth.uid(); bookmarked:=false;
  else
    insert into post_bookmarks(post_id,user_id) values(target_post,auth.uid()); bookmarked:=true;
  end if;
  return bookmarked;
end $$;

create or replace function public.toggle_favorite(target_listing uuid) returns boolean
language plpgsql security definer set search_path=public as $$
declare favorited boolean;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  if not exists(select 1 from listings where id=target_listing and (seller_id=auth.uid() or (moderation_status='approved' and status not in ('draft','removed')) or private.is_staff())) then
    raise exception 'listing_not_available' using errcode='42501';
  end if;
  if exists(select 1 from favorites where listing_id=target_listing and user_id=auth.uid()) then
    delete from favorites where listing_id=target_listing and user_id=auth.uid(); favorited:=false;
  else
    insert into favorites(listing_id,user_id) values(target_listing,auth.uid()); favorited:=true;
  end if;
  return favorited;
end $$;

create or replace function public.create_order(target_listing uuid) returns uuid
language plpgsql security definer set search_path=public as $$
declare item listings%rowtype; new_order uuid;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  select * into item from listings where id=target_listing for update;
  if not found then raise exception 'listing_not_found'; end if;
  if item.seller_id=auth.uid() then raise exception 'cannot_buy_own_listing'; end if;
  if item.status<>'active' or item.moderation_status<>'approved' then raise exception 'listing_unavailable'; end if;
  update listings set status='reserved' where id=item.id;
  insert into orders(listing_id,buyer_id,seller_id,price_cents) values(item.id,auth.uid(),item.seller_id,item.price_cents) returning id into new_order;
  insert into audit_logs(actor_id,action,resource_type,resource_id,result) values(auth.uid(),'CREATE_ORDER','order',new_order::text,'success');
  return new_order;
end $$;

create or replace function public.transition_order(target_order uuid, expected_version integer, next_status public.order_status) returns public.orders
language plpgsql security definer set search_path=public as $$
declare current_order orders%rowtype; allowed boolean:=false;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  select * into current_order from orders where id=target_order for update;
  if not found then raise exception 'order_not_found'; end if;
  if auth.uid() not in (current_order.buyer_id,current_order.seller_id) and not private.is_admin() then raise exception 'forbidden' using errcode='42501'; end if;
  if current_order.version<>expected_version then raise exception 'order_conflict'; end if;
  allowed:=case current_order.status
    when 'pending_payment' then (next_status='paid' and auth.uid()=current_order.buyer_id) or next_status='cancelled'
    when 'paid' then next_status in ('meeting','disputed') or (next_status='cancelled' and private.is_admin())
    when 'meeting' then (next_status='completed' and auth.uid()=current_order.buyer_id) or next_status='disputed'
    when 'disputed' then private.is_admin() and next_status in ('completed','cancelled')
    else false end;
  if not allowed then raise exception 'invalid_order_transition'; end if;
  update orders set status=next_status,version=version+1,completed_at=case when next_status='completed' then now() else completed_at end where id=target_order returning * into current_order;
  if next_status='completed' then update listings set status='sold' where id=current_order.listing_id;
  elsif next_status='cancelled' then update listings set status='active' where id=current_order.listing_id and status='reserved'; end if;
  insert into audit_logs(actor_id,action,resource_type,resource_id,result,metadata) values(auth.uid(),'TRANSITION_ORDER','order',target_order::text,'success',jsonb_build_object('to',next_status));
  return current_order;
end $$;

create or replace function public.soft_delete_time_entry(target_entry uuid, expected_version integer) returns integer
language plpgsql security definer set search_path=public as $$
declare next_version integer;
begin
  update time_entries set deleted_at=now(),version=version+1,sync_state='synced'
  where id=target_entry and user_id=auth.uid() and version=expected_version and deleted_at is null returning version into next_version;
  if next_version is null then raise exception 'time_entry_conflict_or_missing'; end if;
  return next_version;
end $$;

create or replace function public.undo_delete_time_entry(target_entry uuid, expected_version integer) returns integer
language plpgsql security definer set search_path=public as $$
declare next_version integer;
begin
  update time_entries set deleted_at=null,version=version+1,sync_state='synced'
  where id=target_entry and user_id=auth.uid() and version=expected_version and deleted_at is not null returning version into next_version;
  if next_version is null then raise exception 'undo_window_conflict_or_missing'; end if;
  return next_version;
end $$;

create or replace function public.open_conversation(other_user uuid, related_listing uuid default null) returns uuid
language plpgsql security definer set search_path=public as $$
declare found_id uuid;
begin
  if auth.uid() is null or other_user=auth.uid() then raise exception 'invalid_participants'; end if;
  if not exists(select 1 from profiles where id=other_user and not is_blocked) then raise exception 'participant_not_available'; end if;
  if related_listing is not null and not exists(select 1 from listings where id=related_listing and (moderation_status='approved' or seller_id=auth.uid())) then raise exception 'listing_not_available'; end if;
  perform pg_advisory_xact_lock(hashtextextended(least(auth.uid()::text,other_user::text)||greatest(auth.uid()::text,other_user::text)||coalesce(related_listing::text,''),0));
  select c.id into found_id from conversations c
  join conversation_members a on a.conversation_id=c.id and a.user_id=auth.uid()
  join conversation_members b on b.conversation_id=c.id and b.user_id=other_user
  where c.listing_id is not distinct from related_listing limit 1;
  if found_id is null then
    insert into conversations(listing_id) values(related_listing) returning id into found_id;
    insert into conversation_members(conversation_id,user_id) values(found_id,auth.uid()),(found_id,other_user);
  end if;
  return found_id;
end $$;

create or replace function public.admin_set_user_role(target_user uuid, next_role public.app_role) returns void
language plpgsql security definer set search_path=public as $$
begin
  if not private.is_super_admin() then raise exception 'super_admin_required' using errcode='42501'; end if;
  update profiles set role=next_role where id=target_user;
  insert into audit_logs(actor_id,action,resource_type,resource_id,result,metadata) values(auth.uid(),'SET_USER_ROLE','profile',target_user::text,'success',jsonb_build_object('role',next_role));
end $$;

create or replace function public.prevent_audit_mutation() returns trigger language plpgsql as $$ begin raise exception 'audit_logs_are_immutable'; end $$;
drop trigger if exists audit_logs_immutable on public.audit_logs;
create trigger audit_logs_immutable before update or delete on public.audit_logs for each row execute procedure public.prevent_audit_mutation();

revoke all on function public.toggle_post_like(uuid), public.toggle_post_bookmark(uuid), public.toggle_favorite(uuid), public.create_order(uuid), public.transition_order(uuid,integer,public.order_status), public.soft_delete_time_entry(uuid,integer), public.undo_delete_time_entry(uuid,integer), public.open_conversation(uuid,uuid), public.admin_set_user_role(uuid,public.app_role) from public,anon;
grant execute on function public.toggle_post_like(uuid) to authenticated;
grant execute on function public.toggle_post_bookmark(uuid) to authenticated;
grant execute on function public.toggle_favorite(uuid) to authenticated;
grant execute on function public.create_order(uuid) to authenticated;
grant execute on function public.transition_order(uuid,integer,public.order_status) to authenticated;
grant execute on function public.soft_delete_time_entry(uuid,integer) to authenticated;
grant execute on function public.undo_delete_time_entry(uuid,integer) to authenticated;
grant execute on function public.open_conversation(uuid,uuid) to authenticated;
grant execute on function public.admin_set_user_role(uuid,public.app_role) to authenticated;

commit;
