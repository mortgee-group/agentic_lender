import { useState, type FormEvent } from "react";
import { login, type AuthUser } from "../services/auth";
export default function Login({onLogin}: {onLogin: (user: AuthUser) => void}) {
 const [username,setUsername]=useState("");
 const [password,setPassword]=useState("");
 const [loading,setLoading]=useState(false);
 const [error,setError]=useState("");
 async function handleSubmit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setError("");setLoading(true);
  try{onLogin(await login(username.trim(),password));}
  catch(err){setError(err instanceof Error?err.message:"Unable to sign in");}
  finally{setLoading(false);}
 }
 return <main className="auth-shell"><section className="auth-card panel" aria-labelledby="login-title">
  <div className="auth-brand"><span className="mark">⌂</span><span>Agentic Lender</span></div>
  <div className="eyebrow">MORTGAGE BROKER & UNDERWRITER PORTAL</div>
  <h1 id="login-title">Sign in</h1>
  <p className="auth-description">Access your assigned loan pipeline, collateral assessments and AI audit records.</p>
  <form onSubmit={handleSubmit}>
   <div className="field"><label htmlFor="username">Username</label><input id="username" name="username" autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} required autoFocus/></div>
   <div className="field"><label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required/></div>
   {error&&<div className="auth-error" role="alert">{error}</div>}
   <button className="btn primary auth-submit" type="submit" disabled={loading}>{loading?"Signing in…":"Sign in"}</button>
  </form>
  <div className="auth-hint"><strong>Development authentication only.</strong> Sign-in uses local test accounts, not production Firebase roles. Never enter actual customer credentials or financial records.</div>
 </section><p className="auth-footer">AL-Web · College group project · Fictional application data</p></main>;
}
