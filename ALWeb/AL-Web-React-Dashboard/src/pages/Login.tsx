import { useState, type FormEvent } from "react";
import { login, register, type AuthUser } from "../services/auth";
import type { PortalRole } from "../types/roles";

export default function Login({onLogin}: {onLogin: (user: AuthUser) => void}) {
 const [mode,setMode]=useState<"login"|"register">("login");
 const [email,setEmail]=useState("");
 const [password,setPassword]=useState("");
 const [confirmPassword,setConfirmPassword]=useState("");
 const [selectedRole,setSelectedRole]=useState<PortalRole>("borrower");
 const [loading,setLoading]=useState(false);
 const [error,setError]=useState("");

 const switchMode=(next:"login"|"register")=>{
  if(loading)return;
  setMode(next);setError("");setPassword("");setConfirmPassword("");
 };

 async function handleSubmit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setError("");
  if(mode==="register"&&password!==confirmPassword){
   setError("Passwords do not match.");return;
  }
  setLoading(true);
  try{
   const user=mode==="register"
    ? await register(email.trim(),password,selectedRole)
    : await login(email.trim(),password);
   onLogin(user);
  }catch(err){setError(err instanceof Error?err.message:"Unable to complete authentication");}
  finally{setLoading(false);}
 }

 return <main className="auth-shell"><section className="auth-card panel" aria-labelledby="login-title">
  <div className="auth-brand"><span className="mark">⌂</span><span>Agentic Lender</span></div>
  <div className="eyebrow">BORROWER · BROKER · UNDERWRITER PORTAL</div>
  <div className="auth-mode-switch" role="group" aria-label="Account access">
   <button type="button" className={mode==="login"?"active":""} aria-pressed={mode==="login"} onClick={()=>switchMode("login")} disabled={loading}>Sign in</button>
   <button type="button" className={mode==="register"?"active":""} aria-pressed={mode==="register"} onClick={()=>switchMode("register")} disabled={loading}>Register</button>
  </div>
  <h1 id="login-title">{mode==="register"?"Create a test account":"Sign in"}</h1>
  <p className="auth-description">{mode==="register"?"Register with Firebase and select a demo workspace for testing.":"Sign in to your mortgage application workspace."}</p>
  <form onSubmit={handleSubmit}>
   <div className="field"><label htmlFor="auth-email">Email address</label><input id="auth-email" name="email" type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required autoFocus/></div>
   <div className="field"><label htmlFor="auth-password">Password</label><input id="auth-password" name="password" type="password" autoComplete={mode==="register"?"new-password":"current-password"} minLength={mode==="register"?6:undefined} value={password} onChange={e=>setPassword(e.target.value)} required/></div>
   {mode==="register"&&<>
    <div className="field"><label htmlFor="auth-confirm">Confirm password</label><input id="auth-confirm" name="confirmPassword" type="password" autoComplete="new-password" minLength={6} value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} required/></div>
    <div className="field"><label htmlFor="auth-role">Test user role</label>
     <select id="auth-role" name="demoRole" value={selectedRole} onChange={e=>setSelectedRole(e.target.value as PortalRole)} required>
      <option value="borrower">Borrower</option><option value="broker">Mortgage Broker</option><option value="underwriter">Underwriter</option>
     </select>
    </div>
    <div className="notice auth-demo-notice"><strong>Development testing only.</strong> This selection changes the sample dashboard, not your verified permissions. The role is remembered on this browser only.</div>
   </>}
   {error&&<div className="auth-error" role="alert">{error}</div>}
   <button className="btn primary auth-submit" type="submit" disabled={loading}>{loading?(mode==="register"?"Creating account…":"Signing in…"):(mode==="register"?"Create test account":"Sign in")}</button>
  </form>
  <div className="auth-hint"><strong>Firebase email/password authentication.</strong> New registrations are for fictional UI testing only. Do not enter real mortgage/customer information. Access to real records will require backend-enforced roles.</div>
 </section><p className="auth-footer">AL-Web · College group project · Fictional application data</p></main>;
}
