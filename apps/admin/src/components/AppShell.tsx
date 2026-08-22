import { Link, Outlet, useNavigate, useRouterState } from '@tanstack/react-router'
import type { CSSProperties } from 'react'
import { useEffect, useState } from 'react'
import { BrandMark, Symbol } from './BrandMark'
import { SpectraCanvas, type SpectraEnvironment } from './SpectraCanvas'
import { supabase } from '../lib/supabase'

const navigation = [
  ['/', 'dashboard', '总览'], ['/users', 'group', '用户'], ['/content', 'forum', '帖子评论'],
  ['/listings', 'storefront', '商品'], ['/orders', 'receipt_long', '订单'], ['/reports', 'fact_check', '举报审核'],
  ['/announcements', 'campaign', '公告'], ['/releases', 'rocket_launch', '版本发布'], ['/audit', 'policy', '审计日志'],
] as const

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [environment, setEnvironment] = useState<SpectraEnvironment>(() => (localStorage.getItem('spectra-environment') as SpectraEnvironment) || 'original')
  const [motion] = useState(() => localStorage.getItem('spectra-motion') !== 'off' && !matchMedia('(prefers-reduced-motion: reduce)').matches)
  const navigate = useNavigate()
  const path = useRouterState({ select: state => state.location.pathname })
  useEffect(() => { setMenuOpen(false) }, [path])
  useEffect(() => { localStorage.setItem('spectra-environment', environment) }, [environment])

  return <>
    <SpectraCanvas environment={environment} motion={motion} quality="auto" />
    <div className="app-shell">
      <aside className={`sidebar glass strong ${menuOpen ? 'open' : ''}`}>
        <div className="brand"><BrandMark/><span>CampusAI</span></div>
        <nav className="nav" aria-label="管理台导航">
          {navigation.map(([to, icon, label]) => <Link key={to} to={to} className="nav-link" activeProps={{ className:'nav-link active' }}><Symbol>{icon}</Symbol><span>{label}</span></Link>)}
        </nav>
        <div className="nav-spacer" />
        <button className="nav-profile glass" onClick={async()=>{await supabase?.auth.signOut();await navigate({to:'/login'})}}><div className="avatar"><Symbol>person</Symbol></div><div><strong>管理员</strong><div className="muted" style={{fontSize:12}}>退出受保护会话</div></div></button>
      </aside>
      <main className="main">
        <header className="topbar">
          <button className="icon-button mobile-menu" onClick={() => setMenuOpen(v => !v)} aria-label="打开导航"><Symbol>menu</Symbol></button>
          <span className="eyebrow hide-mobile">Campus operations / live</span>
          <div className="toolbar"><EnvironmentMenu value={environment} onChange={setEnvironment}/><button className="icon-button" aria-label="通知"><Symbol>notifications</Symbol></button></div>
        </header>
        <Outlet />
      </main>
    </div>
  </>
}

function EnvironmentMenu({ value, onChange }: { value: SpectraEnvironment; onChange:(value:SpectraEnvironment)=>void }) {
  const [open,setOpen]=useState(false)
  const names: Record<SpectraEnvironment,string>={original:'Original',ocean:'Ocean',ultraviolet:'Ultraviolet',ember:'Ember'}
  return <div style={{position:'relative'}}>
    <button className="pill-button" onClick={()=>setOpen(v=>!v)}><Symbol>blur_on</Symbol><span className="hide-mobile">{names[value]}</span></button>
    {open && <div className="glass strong" style={{position:'absolute',right:0,top:52,zIndex:30,padding:12,borderRadius:18,width:'min(82vw,460px)'}}>
      <div className="environment-grid">
        {(Object.keys(names) as SpectraEnvironment[]).map(item=><button key={item} className={`environment ${value===item?'active':''}`} style={{'--sample': sample[item]} as CSSProperties} onClick={()=>{onChange(item);setOpen(false)}}><span>{names[item]}</span></button>)}
      </div>
    </div>}
  </div>
}

const sample:Record<SpectraEnvironment,string>={
  original:'radial-gradient(circle at 20% 30%,#16c5dc,transparent 45%),radial-gradient(circle at 75% 35%,#7562f5,transparent 48%),radial-gradient(circle at 70% 85%,#ff8b43,transparent 48%)',
  ocean:'radial-gradient(circle at 25% 40%,#16c5dc,transparent 52%),radial-gradient(circle at 75% 60%,#5a7dff,transparent 54%)',
  ultraviolet:'radial-gradient(circle at 25% 40%,#7562f5,transparent 52%),radial-gradient(circle at 75% 60%,#ff79b9,transparent 54%)',
  ember:'radial-gradient(circle at 25% 40%,#ff8b43,transparent 52%),radial-gradient(circle at 75% 60%,#d33f65,transparent 54%)',
}
