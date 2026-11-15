import { useCallback, useEffect, useState } from "react";
import type { AuthUser } from "../services/auth";
import {API_BASE_URL,ApiError,checkBackendHealth,createBorrowerProfile,getCoreProfile,getCoreRoleAccess,verifyCoreAuthentication,type CoreProfile} from "../services/api";

type Step={state:"checking"|"ok"|"missing"|"error";text:string};
const pending:Step={state:"checking",text:"Checking…"};
export default function BackendStatus({user}:{user:AuthUser}) {
 const [health,setHealth]=useState<Step>(pending);
 const [identity,setIdentity]=useState<Step>(pending);
 const [profile,setProfile]=useState<CoreProfile|null>(null);
 const [profileStep,setProfileStep]=useState<Step>(pending);
 const [roleStep,setRoleStep]=useState<Step>(pending);
 const [busy,setBusy]=useState(false);
 const [notice,setNotice]=useState("");

 const check=useCallback(async()=>{
  setHealth(pending);setIdentity(pending);setProfile(null);setProfileStep(pending);setRoleStep(pending);
  try{
   const ok=await checkBackendHealth();
   setHealth({state:ok?"ok":"error",text:ok?"Connected":"Unexpected health response"});
  }catch(e){setHealth({state:"error",text:e instanceof Error?e.message:"Unreachable"});}
  try{
   const result=await verifyCoreAuthentication();
   if(result.uid!==user.uid)throw new Error("Backend UID did not match Firebase UID");
   setIdentity({state:"ok",text:"Firebase ID token verified"});
  }catch(e){
   setIdentity({state:"error",text:e instanceof Error?e.message:"Token verification failed"});
   setProfileStep({state:"missing",text:"Not checked"});setRoleStep({state:"missing",text:"Not checked"});return;
  }
  try{
   const p=await getCoreProfile();setProfile(p);
   setProfileStep({state:"ok",text:`Found: ${p.role}`});
   try{
    await getCoreRoleAccess(p.role);
    setRoleStep({state:"ok",text:`${p.role} endpoint permitted`});
   }catch(e){setRoleStep({state:"error",text:e instanceof Error?e.message:"Role check failed"});}
  }catch(e){
   if(e instanceof ApiError && e.status===404){
    setProfileStep({state:"missing",text:"No Firestore profile (HTTP 404)"});
    setRoleStep({state:"missing",text:"Requires a Firestore profile"});
   }else{
    setProfileStep({state:"error",text:e instanceof Error?e.message:"Profile error"});
    setRoleStep({state:"missing",text:"Not checked"});
   }
  }
 },[user.uid]);
 useEffect(()=>{void check();},[check]);
 async function provision(){
  setBusy(true);setNotice("");
  try{
   await createBorrowerProfile(user.username);
   await check();
   setNotice("Borrower profile created in Firestore.");
  }catch(e){setNotice(e instanceof Error?e.message:"Profile creation failed");}
  finally{setBusy(false);}
 }
 return <section className="panel pad core-status section" aria-label="AL-Core API connection">
  <div className="flex-between"><div><h2>AL-Core connection</h2><p className="core-host">{API_BASE_URL}</p></div>
   <button className="btn secondary" type="button" disabled={busy} onClick={()=>void check()}>Recheck</button></div>
  <div className="core-status-grid">
   <div><strong>Backend health</strong><span data-state={health.state}>{health.text}</span></div>
   <div><strong>Firebase token</strong><span data-state={identity.state}>{identity.text}</span></div>
   <div><strong>Firestore profile</strong><span data-state={profileStep.state}>{profileStep.text}</span></div>
   <div><strong>Backend permission</strong><span data-state={roleStep.state}>{roleStep.text}</span></div>
  </div>
  <p className="core-note">Selected demo UI role: <strong>{user.role}</strong>.
   {profile?<> Verified AL-Core role: <strong>{profile.role}</strong>.</>:<> AL-Core permissions require a backend profile.</>}
   {" "}All displayed loan files are still fictional Web data.</p>
  {identity.state==="ok"&&profileStep.state==="missing"&&user.role==="borrower"&&
   <div className="core-register"><p>This user has no backend profile. The button below writes a permanent Borrower profile to Firestore.</p>
    <button type="button" className="btn secondary" disabled={busy} onClick={()=>void provision()}>{busy?"Creating…":"Create Borrower profile"}</button>
   </div>}
  {notice&&<p role="status" className="core-note">{notice}</p>}
 </section>;
}
