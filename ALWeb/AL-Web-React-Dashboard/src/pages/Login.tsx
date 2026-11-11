import { useState, type FormEvent } from "react";
import { login, type AuthUser } from "../services/auth";
export default function Login({onLogin}: {onLogin: (user: AuthUser) => void}) {
 const [email,setEmail]=useState("");
 const [password,setPassword]=useState("");
 const [loading,setLoading]=useState(false);
 const [error,setError]=useState("");
 async function handleSubmit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setError("");setLoading(true);
  try{onLogin(await login(email.trim(),password));}
  catch(err){setError(err instanceof Error?err.message:"Unable to sign in");}
  finally{setLoading(false);}
 }
 return <main className="auth-shell"><section className="auth-card panel" aria-labelledby="login-title">
  <div className="auth-brand"><span className="mark">⌂</span><span>Agentic Lender</span></div>
  <div className="eyebrow">BORROWER · BROKER · UNDERWRITER PORTAL</div>
  <h1 id="login-title">Sign in</h1>
  <p className="auth-description">Sign in to your assigned mortgage application workspace.</p>
  <form onSubmit={handleSubmit}>
   <div className="field"><label htmlFor="username">Email address</label><input id="username" name="email" type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required autoFocus/></div>
   <div className="field"><label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required/></div>
   {error&&<div className="auth-error" role="alert">{error}</div>}
   <button className="btn primary auth-submit" type="submit" disabled={loading}>{loading?"Signing in…":"Sign in"}</button>
  </form>
  <div className="auth-hint"><strong>Firebase email/password sign-in.</strong> Only the three fictional development accounts are mapped to roles in this prototype. Real mortgage records and production permissions are not enabled.</div>
 </section><p className="auth-footer">AL-Web · College group project · Fictional application data</p></main>;
}
