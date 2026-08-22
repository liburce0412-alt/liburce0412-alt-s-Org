import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, type FormEvent, useMemo, useState } from 'react'
import { Symbol } from '../components/BrandMark'
import { supabase } from '../lib/supabase'

type Kind = 'users'|'content'|'listings'|'orders'|'reports'|'announcements'|'releases'|'audit'
type AdminRecord = { id:string; cells:string[]; raw:Record<string,unknown> }
type ActionSpec = { key:string; label:string; detail:string; requiresNote?:boolean }
type Operation =
  | { type:'action'; kind:Kind; record:AdminRecord; action:ActionSpec; note:string }
  | { type:'create'; kind:'announcements'|'releases'; values:Record<string,string> }

const config:Record<Kind,{eyebrow:string;title:string;description:string;action:string;columns:string[]}>={
  users:{eyebrow:'IDENTITY / ACCESS',title:'用户',description:'账户状态、角色和近期活动。管理员授权只在服务端生效。',action:'导出用户',columns:['用户','角色','状态','最近活动']},
  content:{eyebrow:'COMMUNITY / CONTENT',title:'帖子与评论',description:'按风险和时间处理内容，不让审核离开上下文。',action:'导出内容',columns:['内容','作者','审核','发布时间']},
  listings:{eyebrow:'MARKET / LISTINGS',title:'商品',description:'检查商品状态、价格和媒体，保留已售内容历史。',action:'导出列表',columns:['商品','卖家','状态','价格']},
  orders:{eyebrow:'MARKET / ORDERS',title:'订单',description:'从创建到完成的节点轨迹，以及需要人工介入的争议。',action:'导出订单',columns:['订单','买卖双方','进度','金额']},
  reports:{eyebrow:'TRUST / SAFETY',title:'举报审核',description:'高风险优先，所有处理动作都写入不可变审计轨迹。',action:'导出举报',columns:['举报对象','举报人','状态','提交时间']},
  announcements:{eyebrow:'OPERATIONS / NOTICE',title:'公告',description:'面向不同用户群发布、定时和撤回校园公告。',action:'新建公告',columns:['标题','受众','状态','发布时间']},
  releases:{eyebrow:'DELIVERY / RELEASE',title:'版本发布',description:'Android 构建、校验和、灰度范围与强制升级策略。',action:'新建版本',columns:['版本','构建','状态','发布时间']},
  audit:{eyebrow:'SECURITY / AUDIT',title:'审计日志',description:'按操作者、资源与动作检索服务端审计事件。',action:'导出日志',columns:['操作者','动作','结果','发生时间']},
}

const sampleCells:Record<Kind,string[][]>={
  users:[['林屿 / lin@example.edu','student','正常','2 分钟前'],['陈默 / chen@example.edu','moderator','正常','18 分钟前'],['匿名账户 A19','student','受限','1 小时前']],
  content:[['图书馆三楼学习搭子','匿名同学','待处理','12 分钟前'],['二手群外链评论','林屿','已通过','36 分钟前'],['课程资料分享','陈默','已通过','1 小时前']],
  listings:[['机械键盘 87 键','北区学生','在售','¥168'],['线性代数教材','数学系同学','待处理','¥28'],['折叠露营椅','南苑 5 栋','已售','¥55']],
  orders:[['CA-0822-031','林屿 / 北区学生','待交付','¥168'],['CA-0821-116','陈默 / 数学系同学','已完成','¥28'],['CA-0820-098','匿名 A19 / 南苑 5 栋','争议中','¥55']],
  reports:[['帖子 #P-3821','用户 L17','待处理','8 分钟前'],['商品 #G-992','匿名用户','待处理','21 分钟前'],['评论 #C-811','用户 R04','已完成','1 小时前']],
  announcements:[['新学期数据同步说明','全部用户','已发布','今天 08:00'],['图书馆服务调整','全部用户','定时','明天 07:30'],['市场交易提醒','全部用户','草稿','—']],
  releases:[['1.2.0','120 / a8f2','草稿','—'],['1.1.4','114 / e921','已发布','8月12日'],['1.1.3','113 / b20d','已归档','7月28日']],
  audit:[['moderator@campus.ai','APPROVE_REPORT','成功','2 分钟前'],['admin@campus.ai','PUBLISH_NOTICE','成功','18 分钟前'],['system','SYNC_ORDER','重试','26 分钟前']],
}
const samples = Object.fromEntries(Object.entries(sampleCells).map(([kind, rows])=>[kind,rows.map((cells,index)=>({id:`sample-${kind}-${index}`,cells,raw:{}}))])) as Record<Kind,AdminRecord[]>

export function DataPage({kind}:{kind:Kind}) {
  const page=config[kind]
  const queryClient=useQueryClient()
  const [selected,setSelected]=useState<Set<string>>(new Set())
  const [search,setSearch]=useState('')
  const [actionableOnly,setActionableOnly]=useState(false)
  const [descending,setDescending]=useState(true)
  const [actionRecord,setActionRecord]=useState<AdminRecord|null>(null)
  const [pending,setPending]=useState<{record:AdminRecord;action:ActionSpec}|null>(null)
  const [createOpen,setCreateOpen]=useState(false)
  const {data:rows=samples[kind],isLoading,error}=useQuery({queryKey:['admin-data',kind],queryFn:()=>loadRows(kind)})
  const operation=useMutation({
    mutationFn:executeOperation,
    onSuccess:async()=>{setPending(null);setCreateOpen(false);setSelected(new Set());await queryClient.invalidateQueries({queryKey:['admin-data',kind]})},
  })
  const visibleRows=useMemo(()=>{
    const needle=search.trim().toLocaleLowerCase()
    const filtered=rows.filter(row=>(!needle||row.cells.some(value=>value.toLocaleLowerCase().includes(needle)))&&(!actionableOnly||actionsFor(kind,row).length>0))
    return descending?filtered:[...filtered].reverse()
  },[rows,search,actionableOnly,descending,kind])
  const toggle=(id:string)=>setSelected(current=>{const next=new Set(current);if(next.has(id))next.delete(id);else next.add(id);return next})
  const selectedRows=rows.filter(row=>selected.has(row.id))
  const pageAction=()=>{
    if(kind==='announcements'||kind==='releases')setCreateOpen(true)
    else downloadCsv(page.title,rows)
  }

  return <>
    <div className="page-header"><div><div className="eyebrow">{page.eyebrow}</div><h1>{page.title}</h1><p className="muted">{page.description}</p></div><button className="pill-button primary" disabled={!supabase} title={!supabase?'连接 Supabase 后可用':undefined} onClick={pageAction}><Symbol>{kind==='announcements'||kind==='releases'?'add':'download'}</Symbol>{page.action}</button></div>
    <div className="filters"><label className="search-field glass"><Symbol>search</Symbol><input value={search} onChange={event=>setSearch(event.target.value)} placeholder="搜索当前结果" aria-label="搜索当前结果"/></label><button className={`pill-button ${actionableOnly?'primary':''}`} onClick={()=>setActionableOnly(value=>!value)}><Symbol>filter_list</Symbol>{actionableOnly?'仅待处理':'全部状态'}</button><button className="pill-button" onClick={()=>setDescending(value=>!value)}><Symbol>sort</Symbol>{descending?'最近优先':'较早优先'}</button></div>
    {error&&<div className="error-bar" role="alert">读取失败：{error instanceof Error?error.message:'未知错误'}。请检查权限或网络后刷新。</div>}
    {operation.error&&<div className="error-bar" role="alert">操作失败：{operation.error instanceof Error?operation.error.message:'未知错误'}。数据没有被静默修改，请刷新后重试。</div>}
    <div className="table-panel glass strong" style={{marginTop:14}}>
      <div className="table-row header"><span/><span>{page.columns[0]}</span><span>{page.columns[1]}</span><span>{page.columns[2]}</span><span>{page.columns[3]}</span><span/></div>
      {isLoading&&<div className="empty-row muted">正在读取受保护数据…</div>}
      {!isLoading&&visibleRows.length===0&&<div className="empty-row muted">没有匹配结果。</div>}
      {visibleRows.map(row=><div className="table-row" key={row.id}>
        <input className="check" type="checkbox" checked={selected.has(row.id)} onChange={()=>toggle(row.id)} aria-label={`选择 ${row.cells[0]}`}/>
        <strong>{row.cells[0]}</strong><span className="muted">{row.cells[1]}</span><span><Status value={row.cells[2]}/></span><span className="muted">{row.cells[3]}</span><button className="icon-button" aria-label={`处理 ${row.cells[0]}`} disabled={!supabase||actionsFor(kind,row).length===0} onClick={()=>setActionRecord(row)}><Symbol>more_horiz</Symbol></button>
      </div>)}
    </div>
    {selected.size>0&&<div className="bulk-bar glass strong"><strong>已选 {selected.size} 项</strong><button className="pill-button" onClick={()=>downloadCsv(`${page.title}-所选`,selectedRows)}>导出所选</button><button className="pill-button primary" disabled={!supabase||!selectedRows.some(row=>actionsFor(kind,row).length)} onClick={()=>setActionRecord(selectedRows.find(row=>actionsFor(kind,row).length>0)??null)}>逐项处理</button></div>}
    {actionRecord&&<ActionSheet title={actionRecord.cells[0]} actions={actionsFor(kind,actionRecord)} onSelect={action=>{setActionRecord(null);setPending({record:actionRecord,action})}} onClose={()=>setActionRecord(null)}/>}
    {pending&&<ConfirmAction pending={pending} busy={operation.isPending} onClose={()=>setPending(null)} onConfirm={note=>operation.mutate({type:'action',kind,record:pending.record,action:pending.action,note})}/>}
    {createOpen&&(kind==='announcements'||kind==='releases')&&<CreateDialog kind={kind} busy={operation.isPending} onClose={()=>setCreateOpen(false)} onSubmit={values=>operation.mutate({type:'create',kind,values})}/>}
  </>
}

function Status({value}:{value:string}) { const kind=/高|受限|争议|重试|移除|拒绝/.test(value)?'error':/中|待|定时|草稿/.test(value)?'warn':/正常|成功|完成|发布|在售|通过/.test(value)?'ok':'';return <span className={`badge ${kind}`}>{value}</span> }

function ActionSheet({title,actions,onSelect,onClose}:{title:string;actions:ActionSpec[];onSelect:(action:ActionSpec)=>void;onClose:()=>void}){
  return <div className="modal-backdrop" role="presentation" onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}}><section className="modal-card glass strong" role="dialog" aria-modal="true" aria-label={`处理 ${title}`}><div className="panel-header"><div><div className="eyebrow">SELECT ACTION</div><h2>{title}</h2></div><button className="icon-button" onClick={onClose} aria-label="关闭"><Symbol>close</Symbol></button></div><div className="action-list">{actions.map(action=><button className="action-row" key={action.key} onClick={()=>onSelect(action)}><span><strong>{action.label}</strong><small>{action.detail}</small></span><Symbol>chevron_right</Symbol></button>)}</div></section></div>
}

function ConfirmAction({pending,busy,onClose,onConfirm}:{pending:{record:AdminRecord;action:ActionSpec};busy:boolean;onClose:()=>void;onConfirm:(note:string)=>void}){
  const [note,setNote]=useState('')
  const [progress,setProgress]=useState(0)
  const commit=()=>{if(progress>=95&&!busy&&(!pending.action.requiresNote||note.trim()))onConfirm(note.trim())}
  return <div className="modal-backdrop"><section className="modal-card glass strong" role="dialog" aria-modal="true" aria-label={pending.action.label}><div className="eyebrow">CONFIRM CHANGE</div><h2>{pending.action.label}</h2><p>{pending.action.detail}</p><p className="muted">对象：{pending.record.cells[0]}</p>{pending.action.requiresNote&&<label className="field"><span>处理说明</span><textarea rows={4} value={note} onChange={event=>setNote(event.target.value.slice(0,1000))} required/></label>}<label className="slide-confirm"><span>{busy?'正在提交…':'向右滑动确认'}</span><input aria-label="滑动确认" type="range" min="0" max="100" value={progress} disabled={busy||Boolean(pending.action.requiresNote&&!note.trim())} onChange={event=>setProgress(Number(event.target.value))} onPointerUp={commit} onKeyUp={event=>{if(event.key==='Enter'||event.key===' ')commit()}}/></label><button className="pill-button" style={{width:'100%',marginTop:10}} disabled={busy} onClick={onClose}>暂不操作</button></section></div>
}

function CreateDialog({kind,busy,onClose,onSubmit}:{kind:'announcements'|'releases';busy:boolean;onClose:()=>void;onSubmit:(values:Record<string,string>)=>void}){
  const [values,setValues]=useState<Record<string,string>>({})
  const field=(name:string)=>({value:values[name]??'',onChange:(event:ChangeEvent<HTMLInputElement|HTMLTextAreaElement>)=>setValues(current=>({...current,[name]:event.target.value}))})
  const submit=(event:FormEvent)=>{event.preventDefault();onSubmit(values)}
  return <div className="modal-backdrop"><form className="modal-card glass strong" role="dialog" aria-modal="true" onSubmit={submit}><div className="panel-header"><div><div className="eyebrow">CREATE</div><h2>{kind==='announcements'?'新建公告':'新建版本'}</h2></div><button type="button" className="icon-button" onClick={onClose} aria-label="关闭"><Symbol>close</Symbol></button></div>{kind==='announcements'?<><label className="field"><span>标题</span><input {...field('title')} maxLength={160} required/></label><label className="field"><span>正文</span><textarea {...field('body')} rows={7} maxLength={10000} required/></label></>:<><label className="field"><span>版本名称</span><input {...field('version_name')} placeholder="1.2.0" maxLength={40} required/></label><label className="field"><span>版本代码</span><input {...field('version_code')} type="number" min="1" required/></label><label className="field"><span>版本说明</span><textarea {...field('notes')} rows={5} maxLength={10000} required/></label><label className="field"><span>APK HTTPS 地址</span><input {...field('apk_url')} type="url" pattern="https://.*" required/></label><label className="field"><span>SHA-256</span><input {...field('checksum')} pattern="[0-9a-fA-F]{64}" required/></label></>}<button className="pill-button primary" style={{width:'100%',marginTop:12}} disabled={busy}>{busy?'正在保存…':'保存为草稿'}</button></form></div>
}

function actionsFor(kind:Kind,record:AdminRecord):ActionSpec[]{
  const status=String(kind==='content'||kind==='listings'?record.raw.moderation_status??'':record.raw.status??'')
  if(kind==='users')return [{key:record.raw.is_blocked?'unblock':'block',label:record.raw.is_blocked?'解除限制':'限制账户',detail:record.raw.is_blocked?'恢复该用户的校园服务访问。':'立即阻止该用户继续访问受保护业务。'}]
  if(kind==='content'||kind==='listings'){
    if(status==='pending')return [{key:'approve',label:'通过审核',detail:'内容将对符合条件的用户可见。'},{key:'reject',label:'拒绝内容',detail:'内容保留记录，但不会公开展示。'}]
    if(status==='approved')return [{key:'remove',label:'移除内容',detail:'内容将停止公开展示，历史和审计记录仍保留。'}]
  }
  if(kind==='orders'&&status==='disputed')return [{key:'complete_order',label:'裁定完成',detail:'订单将完成，商品标记为已售。'},{key:'cancel_order',label:'裁定取消',detail:'订单将取消，商品恢复为在售。'}]
  if(kind==='reports'&&status==='pending')return [{key:'resolve_report',label:'确认并解决',detail:'记录处理说明并关闭举报。',requiresNote:true},{key:'dismiss_report',label:'驳回举报',detail:'说明驳回原因并关闭举报。',requiresNote:true}]
  if(kind==='announcements')return status==='published'?[{key:'withdraw_announcement',label:'撤回公告',detail:'公告将不再对用户展示。'}]:[{key:'publish_announcement',label:'发布公告',detail:'公告将立即面向设定受众展示。'}]
  if(kind==='releases')return status==='published'?[{key:'archive_release',label:'归档版本',detail:'版本保留记录，但不再作为当前发布展示。'}]:status==='draft'?[{key:'publish_release',label:'发布版本',detail:'版本将进入客户端发布列表。'}]:[]
  return []
}

async function executeOperation(operation:Operation){
  if(!supabase)throw new Error('管理台尚未连接 Supabase。')
  if(operation.type==='create'){
    const {error}=operation.kind==='announcements'
      ?await supabase.rpc('admin_create_announcement',{announcement_title:operation.values.title,announcement_body:operation.values.body})
      :await supabase.rpc('admin_create_release',{release_version_code:Number(operation.values.version_code),release_version_name:operation.values.version_name,release_notes:operation.values.notes,release_apk_url:operation.values.apk_url,release_checksum_sha256:operation.values.checksum})
    if(error)throw error
    return
  }
  const {kind,record,action,note}=operation
  let result:{error:unknown}|null=null
  if(kind==='users')result=await supabase.rpc('admin_set_user_blocked',{target_user:record.id,blocked:action.key==='block'})
  else if(kind==='content'||kind==='listings')result=await supabase.rpc('moderate_content',{target_type:kind==='listings'?'listing':String(record.raw.content_type??'post'),target_id:record.id,next_status:action.key==='approve'?'approved':action.key==='reject'?'rejected':'removed'})
  else if(kind==='orders')result=await supabase.rpc('transition_order',{target_order:record.id,expected_version:Number(record.raw.version),next_status:action.key==='complete_order'?'completed':'cancelled'})
  else if(kind==='reports')result=await supabase.rpc('resolve_report',{target_report:record.id,next_status:action.key==='resolve_report'?'resolved':'dismissed',resolution_text:note})
  else if(kind==='announcements')result=await supabase.rpc('admin_set_announcement_status',{target_announcement:record.id,next_status:action.key==='publish_announcement'?'published':'withdrawn'})
  else if(kind==='releases')result=await supabase.rpc('admin_set_release_status',{target_release:record.id,next_status:action.key==='publish_release'?'published':'archived'})
  if(result?.error)throw result.error
}

async function loadRows(kind:Kind):Promise<AdminRecord[]>{
  if(!supabase)return samples[kind]
  if(kind==='content'){
    const [posts,comments]=await Promise.all([
      supabase.from('posts').select('id,body,author_id,moderation_status,created_at').order('created_at',{ascending:false}).limit(100),
      supabase.from('comments').select('id,body,author_id,moderation_status,created_at').order('created_at',{ascending:false}).limit(100),
    ])
    if(posts.error)throw posts.error
    if(comments.error)throw comments.error
    return [...(posts.data??[]).map(item=>toRecord(kind,{...item,content_type:'post'})),...(comments.data??[]).map(item=>toRecord(kind,{...item,content_type:'comment'}))].sort((a,b)=>String(b.raw.created_at).localeCompare(String(a.raw.created_at)))
  }
  const query=kind==='users'?supabase.from('profiles').select('id,display_name,handle,role,is_blocked,updated_at,created_at'):
    kind==='listings'?supabase.from('listings').select('id,title,seller_id,status,moderation_status,price_cents,created_at'):
    kind==='orders'?supabase.from('orders').select('id,buyer_id,seller_id,status,version,price_cents,created_at'):
    kind==='reports'?supabase.from('reports').select('id,target_type,target_id,reporter_id,status,created_at'):
    kind==='announcements'?supabase.from('announcements').select('id,title,audience,status,publish_at,created_at'):
    kind==='releases'?supabase.from('app_releases').select('id,version_name,version_code,status,published_at,created_at'):
    supabase.from('audit_logs').select('id,actor_id,action,result,created_at')
  const {data,error}=await query.order('created_at',{ascending:false}).limit(100)
  if(error)throw error
  return (data??[]).map((item:Record<string,unknown>)=>toRecord(kind,item))
}

function toRecord(kind:Kind,item:Record<string,unknown>):AdminRecord{
  const date=formatDate(item.updated_at??item.publish_at??item.published_at??item.created_at)
  const short=(value:unknown)=>String(value??'—').slice(0,12)
  const id=String(item.id??`${kind}-${Math.random()}`)
  if(kind==='users')return {id,raw:item,cells:[`${item.display_name??'未命名'} / ${item.handle??'—'}`,String(item.role??'student'),item.is_blocked?'受限':'正常',date]}
  if(kind==='content')return {id,raw:item,cells:[`${item.content_type==='comment'?'评论':'帖子'} · ${String(item.body??'').slice(0,38)}`,short(item.author_id),statusLabel(item.moderation_status),date]}
  if(kind==='listings')return {id,raw:item,cells:[String(item.title??'未命名'),short(item.seller_id),`${statusLabel(item.moderation_status)} · ${statusLabel(item.status)}`,money(item.price_cents)]}
  if(kind==='orders')return {id,raw:item,cells:[short(item.id),`${short(item.buyer_id)} / ${short(item.seller_id)}`,statusLabel(item.status),money(item.price_cents)]}
  if(kind==='reports')return {id,raw:item,cells:[`${item.target_type??'内容'} / ${short(item.target_id)}`,short(item.reporter_id),statusLabel(item.status),date]}
  if(kind==='announcements')return {id,raw:item,cells:[String(item.title??'未命名'),JSON.stringify(item.audience??{}).slice(0,24),statusLabel(item.status),date]}
  if(kind==='releases')return {id,raw:item,cells:[String(item.version_name??'—'),String(item.version_code??'—'),statusLabel(item.status),date]}
  return {id,raw:item,cells:[short(item.actor_id),String(item.action??'—'),statusLabel(item.result),date]}
}

function downloadCsv(title:string,rows:AdminRecord[]){const content=rows.map(row=>row.cells.map(value=>`"${value.replaceAll('"','""')}"`).join(',')).join('\n');const url=URL.createObjectURL(new Blob([`\uFEFF${content}`],{type:'text/csv;charset=utf-8'}));const anchor=document.createElement('a');anchor.href=url;anchor.download=`${title}.csv`;anchor.click();URL.revokeObjectURL(url)}
function money(value:unknown){const cents=Number(value);return Number.isFinite(cents)?`¥${(cents/100).toFixed(2)}`:'—'}
function formatDate(value:unknown){if(!value)return '—';const date=new Date(String(value));return Number.isNaN(date.valueOf())?'—':date.toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'})}
function statusLabel(value:unknown){const labels:Record<string,string>={pending:'待处理',approved:'已通过',rejected:'已拒绝',removed:'已移除',resolved:'已完成',dismissed:'已驳回',active:'在售',reserved:'已预订',sold:'已售',withdrawn:'已下架',pending_payment:'待付款',paid:'已付款',meeting:'待交付',completed:'已完成',cancelled:'已取消',disputed:'争议中',draft:'草稿',scheduled:'定时',published:'已发布',archived:'已归档',success:'成功',retry:'重试'};return labels[String(value)]??String(value??'—')}
