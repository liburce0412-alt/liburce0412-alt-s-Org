begin;

alter table public.messages drop constraint if exists messages_body_length;
alter table public.messages add constraint messages_body_length
  check (char_length(body) between 0 and 4000 and (body <> '' or media_path is not null));

create or replace function public.list_conversation_summaries()
returns table(
  id uuid,
  listing_id uuid,
  listing_title text,
  other_user_id uuid,
  other_name text,
  last_message text,
  last_message_at timestamptz,
  unread_count integer
)
language sql stable security definer set search_path = ''
as $$
  select
    c.id,
    c.listing_id,
    coalesce(l.title, ''),
    peer.user_id,
    coalesce(p.display_name, 'CampusAI 用户'),
    coalesce(latest.body, ''),
    coalesce(latest.created_at, c.updated_at),
    coalesce(unread.total, 0)::integer
  from public.conversation_members self_member
  join public.conversations c on c.id = self_member.conversation_id
  left join public.listings l on l.id = c.listing_id
  left join lateral (
    select member.user_id
    from public.conversation_members member
    where member.conversation_id = c.id
      and member.user_id <> (select auth.uid())
    order by member.created_at
    limit 1
  ) peer on true
  left join public.profiles p on p.id = peer.user_id
  left join lateral (
    select message.body, message.created_at
    from public.messages message
    where message.conversation_id = c.id and message.deleted_at is null
    order by message.created_at desc
    limit 1
  ) latest on true
  left join lateral (
    select count(*)::integer as total
    from public.messages message
    where message.conversation_id = c.id
      and message.deleted_at is null
      and message.sender_id <> (select auth.uid())
      and message.created_at > coalesce(self_member.last_read_at, '-infinity'::timestamptz)
  ) unread on true
  where self_member.user_id = (select auth.uid())
  order by coalesce(latest.created_at, c.updated_at) desc;
$$;

create or replace function public.create_comment(
  target_post uuid,
  comment_body text,
  parent_comment uuid default null
)
returns public.comments
language plpgsql security definer set search_path = ''
as $$
declare
  created_comment public.comments%rowtype;
  normalized_body text := btrim(coalesce(comment_body, ''));
begin
  if auth.uid() is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  if char_length(normalized_body) not between 1 and 2000 then
    raise exception 'comment_body_invalid' using errcode = '22023';
  end if;
  if not exists(
    select 1 from public.posts post
    where post.id = target_post and post.deleted_at is null
      and (post.moderation_status = 'approved' or post.author_id = auth.uid() or private.is_staff())
  ) then
    raise exception 'post_not_available' using errcode = '42501';
  end if;
  if parent_comment is not null and not exists(
    select 1 from public.comments comment
    where comment.id = parent_comment and comment.post_id = target_post and comment.deleted_at is null
  ) then
    raise exception 'parent_comment_not_available' using errcode = '22023';
  end if;

  insert into public.comments(post_id, author_id, parent_id, body)
  values(target_post, auth.uid(), parent_comment, normalized_body)
  returning * into created_comment;

  return created_comment;
end;
$$;

create or replace function public.mark_conversation_read(target_conversation uuid)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;

  update public.conversation_members
  set last_read_at = now()
  where conversation_id = target_conversation and user_id = auth.uid();

  if not found then
    raise exception 'conversation_not_available' using errcode = '42501';
  end if;
end;
$$;

create or replace function public.send_message(
  target_conversation uuid,
  client_message uuid,
  message_body text
)
returns public.messages
language plpgsql security definer set search_path = ''
as $$
declare
  sent public.messages%rowtype;
  normalized_body text := btrim(coalesce(message_body, ''));
begin
  if auth.uid() is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  if char_length(normalized_body) not between 1 and 4000 then
    raise exception 'message_body_invalid' using errcode = '22023';
  end if;
  if not private.is_conversation_member(target_conversation, auth.uid())
    or not exists(select 1 from public.profiles p where p.id = auth.uid() and not p.is_blocked) then
    raise exception 'conversation_not_available' using errcode = '42501';
  end if;

  insert into public.messages(conversation_id, sender_id, body, client_id)
  values(target_conversation, auth.uid(), normalized_body, client_message)
  on conflict(sender_id, client_id) do update set client_id = excluded.client_id
  returning * into sent;

  update public.conversations
  set updated_at = greatest(updated_at, sent.created_at)
  where id = target_conversation;

  return sent;
end;
$$;

create or replace function public.list_my_orders()
returns table(
  id uuid,
  listing_id uuid,
  listing_title text,
  listing_media_paths text[],
  buyer_id uuid,
  buyer_name text,
  seller_id uuid,
  seller_name text,
  price_cents integer,
  status public.order_status,
  version integer,
  created_at timestamptz,
  updated_at timestamptz
)
language sql stable security definer set search_path = ''
as $$
  select
    orders.id,
    orders.listing_id,
    coalesce(listing.title, '校园交易'),
    coalesce(listing.media_paths, '{}'::text[]),
    orders.buyer_id,
    coalesce(buyer.display_name, '买家'),
    orders.seller_id,
    coalesce(seller.display_name, '卖家'),
    orders.price_cents,
    orders.status,
    orders.version,
    orders.created_at,
    orders.updated_at
  from public.orders orders
  join public.listings listing on listing.id = orders.listing_id
  join public.profiles buyer on buyer.id = orders.buyer_id
  join public.profiles seller on seller.id = orders.seller_id
  where auth.uid() in (orders.buyer_id, orders.seller_id)
  order by orders.updated_at desc;
$$;

create or replace function public.open_conversation(other_user uuid, related_listing uuid default null)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  found_id uuid;
  listing_seller uuid;
begin
  if auth.uid() is null or other_user = auth.uid() then
    raise exception 'invalid_participants';
  end if;
  if not exists(select 1 from public.profiles where id = other_user and not is_blocked) then
    raise exception 'participant_not_available';
  end if;
  if related_listing is not null then
    select seller_id into listing_seller
    from public.listings
    where id = related_listing
      and (moderation_status = 'approved' or seller_id = auth.uid());
    if listing_seller is null or (auth.uid() <> listing_seller and other_user <> listing_seller) then
      raise exception 'listing_not_available' using errcode = '42501';
    end if;
  end if;

  perform pg_advisory_xact_lock(hashtextextended(
    least(auth.uid()::text, other_user::text)
    || greatest(auth.uid()::text, other_user::text)
    || coalesce(related_listing::text, ''), 0
  ));

  select conversation.id into found_id
  from public.conversations conversation
  join public.conversation_members self_member
    on self_member.conversation_id = conversation.id and self_member.user_id = auth.uid()
  join public.conversation_members peer_member
    on peer_member.conversation_id = conversation.id and peer_member.user_id = other_user
  where conversation.listing_id is not distinct from related_listing
  limit 1;

  if found_id is null then
    insert into public.conversations(listing_id) values(related_listing) returning id into found_id;
    insert into public.conversation_members(conversation_id, user_id)
    values(found_id, auth.uid()), (found_id, other_user);
  end if;
  return found_id;
end;
$$;

create or replace function public.transition_order(
  target_order uuid,
  expected_version integer,
  next_status public.order_status
)
returns public.orders
language plpgsql security definer set search_path = ''
as $$
declare
  current_order public.orders%rowtype;
  allowed boolean := false;
begin
  if auth.uid() is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  select * into current_order from public.orders where id = target_order for update;
  if not found then raise exception 'order_not_found'; end if;
  if auth.uid() not in (current_order.buyer_id, current_order.seller_id) and not private.is_admin() then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  if current_order.version <> expected_version then raise exception 'order_conflict'; end if;

  allowed := case current_order.status
    when 'pending_payment' then
      (next_status = 'paid' and auth.uid() = current_order.buyer_id)
      or (next_status = 'cancelled' and auth.uid() in (current_order.buyer_id, current_order.seller_id))
    when 'paid' then
      (next_status = 'meeting' and auth.uid() = current_order.seller_id)
      or (next_status = 'disputed' and auth.uid() in (current_order.buyer_id, current_order.seller_id))
      or (next_status = 'cancelled' and private.is_admin())
    when 'meeting' then
      (next_status = 'completed' and auth.uid() = current_order.buyer_id)
      or (next_status = 'disputed' and auth.uid() in (current_order.buyer_id, current_order.seller_id))
    when 'disputed' then private.is_admin() and next_status in ('completed', 'cancelled')
    else false
  end;
  if not allowed then raise exception 'invalid_order_transition'; end if;

  update public.orders
  set status = next_status,
      version = version + 1,
      completed_at = case when next_status = 'completed' then now() else completed_at end
  where id = target_order
  returning * into current_order;

  if next_status = 'completed' then
    update public.listings set status = 'sold' where id = current_order.listing_id;
  elsif next_status = 'cancelled' then
    update public.listings set status = 'active' where id = current_order.listing_id and status = 'reserved';
  end if;

  insert into public.audit_logs(actor_id, action, resource_type, resource_id, result, metadata)
  values(auth.uid(), 'TRANSITION_ORDER', 'order', target_order::text, 'success', jsonb_build_object('to', next_status));
  return current_order;
end;
$$;

revoke insert on public.messages from authenticated;
revoke insert on public.comments from authenticated;
revoke all on function public.list_conversation_summaries(), public.mark_conversation_read(uuid),
  public.send_message(uuid, uuid, text), public.list_my_orders(), public.create_comment(uuid, text, uuid)
from public, anon;
revoke all on function public.open_conversation(uuid, uuid),
  public.transition_order(uuid, integer, public.order_status)
from public, anon;

grant execute on function public.list_conversation_summaries() to authenticated;
grant execute on function public.mark_conversation_read(uuid) to authenticated;
grant execute on function public.send_message(uuid, uuid, text) to authenticated;
grant execute on function public.list_my_orders() to authenticated;
grant execute on function public.create_comment(uuid, text, uuid) to authenticated;
grant execute on function public.open_conversation(uuid, uuid) to authenticated;
grant execute on function public.transition_order(uuid, integer, public.order_status) to authenticated;

commit;
