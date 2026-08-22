import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { BrandMark, Symbol } from '../components/BrandMark'
import { SpectraCanvas } from '../components/SpectraCanvas'
import { hasAdminRole, isSupabaseConfigured, supabase } from '../lib/supabase'

const schema=z.object({email:z.email('请输入有效邮箱'),password:z.string().min(8,'密码至少 8 位')})
type Form=z.infer<typeof schema>

export function LoginPage(){
  const navigate=useNavigate();const [serverError,setServerError]=useState('')
  const {register,handleSubmit,formState:{errors,isSubmitting}}=useForm<Form>({resolver:zodResolver(schema)})
  const submit=handleSubmit(async values=>{
    setServerError('')
    if(!supabase){setServerError('管理台尚未配置 Supabase 发布密钥。请先填写 .env.local，再重试。');return}
    const {data,error}=await supabase.auth.signInWithPassword(values)
    if(error){setServerError(`登录失败：${error.message}。请检查账号或联系超级管理员。`);return}
    if(!data.user || !(await hasAdminRole(data.user.id))){
      await supabase.auth.signOut()
      setServerError('这个账号没有管理台权限。请联系超级管理员分配角色。')
      return
    }
    await navigate({to:'/'})
  })
  return <><SpectraCanvas environment="original" motion quality="auto"/><div className="login-wrap"><form className="login-card glass strong" onSubmit={submit}>
    <div className="login-brand"><BrandMark/><div><h2>CampusAI</h2><div className="muted">安全管理入口</div></div></div>
    <div className="field"><label htmlFor="email">邮箱</label><input id="email" type="email" autoComplete="username" {...register('email')}/>{errors.email&&<span className="error-text">{errors.email.message}</span>}</div>
    <div className="field"><label htmlFor="password">密码</label><input id="password" type="password" autoComplete="current-password" {...register('password')}/>{errors.password&&<span className="error-text">{errors.password.message}</span>}</div>
    {serverError&&<p className="error-text" role="alert">{serverError}</p>}
    <button className="pill-button primary" style={{width:'100%',marginTop:10}} disabled={isSubmitting}><Symbol>login</Symbol>{isSubmitting?'正在验证':'登录管理台'}</button>
    {!isSupabaseConfigured&&<p className="muted" style={{fontSize:12}}>当前为未配置状态；不会把任何密钥写入浏览器包。</p>}
  </form></div></>
}
