import {useState} from "react";
import {ApiError,submitHumanDecision,DEVELOPMENT_MORTGAGE_RATE_BPS,displayRate,type CoreApplication,type HumanDecision} from "../services/api";
import type {PortalRole} from "../types/roles";

export default function HumanDecision({application,role,onUpdated,aiResultsAvailable}:{
 application:CoreApplication;role:PortalRole;onUpdated:()=>void;aiResultsAvailable:boolean;
}){
 const [choice,setChoice]=useState<""|HumanDecision>("");
 const [confirmed,setConfirmed]=useState(false);
 const [rateConfirmed,setRateConfirmed]=useState(false);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState("");
 const [success,setSuccess]=useState(false);
 if(role!=="underwriter")return null;
 const eligible=application.lifecycleStatus==="AI_DELIBERATED" && !application.underwriterDecision;
 const submit=async()=>{
  if(!eligible||!aiResultsAvailable||!choice||!confirmed||(choice==="APPROVED"&&!rateConfirmed)||busy)return;
  setBusy(true);setError("");
  try{
   await submitHumanDecision(application.applicationId,choice,choice==="APPROVED"&&rateConfirmed);
   setSuccess(true);
   setConfirmed(false);
   setRateConfirmed(false);
   onUpdated();
  }catch(e){
   setError(e instanceof ApiError&&e.status===409
    ?"This application is no longer awaiting a human decision. Refresh the details to see its latest status."
    :e instanceof Error?e.message:"Unable to save the human decision.");
  }finally{setBusy(false);}
 };
 return <section className="panel pad section human-decision" aria-label="Final human underwriter decision">
  <span className="eyebrow">Human review · Underwriter only</span>
  <h2>Final Underwriter decision</h2>
  <p className="core-note">A human Underwriter must review the loan file and AI findings. The AI recommendation is advisory; this decision is recorded by AL-Core in Firestore.</p>
  {application.underwriterDecision?<div className="human-decision-record" role="status">
   <strong>Recorded decision: {application.underwriterDecision==="APPROVED"?"Approved":"Rejected"}</strong>
   {application.approvedLoanAmount!=null&&<div className="detail-row"><span>Approved amount</span><strong>{new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR"}).format(application.approvedLoanAmount)}</strong></div>}
   {application.approvedAprBasisPoints!=null&&<div className="detail-row"><span>Approved product rate</span><strong>{displayRate(application.approvedAprBasisPoints)} ({application.approvedAprBasisPoints} bps)</strong></div>}
   <div className="detail-row"><span>Lifecycle state</span><strong>{application.lifecycleStatus}</strong></div>
   <div className="detail-row"><span>Decided by (UID)</span><strong>{application.decidedBy??"—"}</strong></div>
   <div className="detail-row"><span>Decision timestamp</span><strong>{application.decidedAt?new Date(application.decidedAt).toLocaleString("en-IE"):"—"}</strong></div>
   <p className="core-note">Recorded decisions cannot be changed through this form.</p>
  </div>:success?<p role="status">Decision saved by AL-Core. Refreshing the application…</p>
  :!eligible?<p className="notice" role="status">Final approval or rejection is available only after AI underwriting has finished and the application is in <strong>AI_DELIBERATED</strong> status. Current status: <strong>{application.lifecycleStatus}</strong>.</p>
  :!aiResultsAvailable?<div className="notice" role="status"><strong>AI underwriting review required.</strong> This application is AI_DELIBERATED, but detailed AI underwriting evidence has not been loaded. Reload the saved AI evidence above. Final approval and rejection remain disabled until the real AI findings are available to review.</div>
  :<div className="human-decision-form">
   <div className="field"><label htmlFor="human-decision-select">Human decision</label>
    <select id="human-decision-select" value={choice} disabled={busy} onChange={e=>{setChoice(e.target.value as ""|HumanDecision);setConfirmed(false);setRateConfirmed(false);setError("");}}>
     <option value="">Select a decision…</option>
     <option value="APPROVED">Approve mortgage</option>
     <option value="REJECTED">Reject mortgage</option>
    </select>
   </div>
   {choice&&<p className="human-decision-warning"><strong>{choice==="APPROVED"?"Approve":"Reject"} this mortgage?</strong> This will persist a final Underwriter decision and update the application lifecycle. It cannot be undone using this screen.</p>}
   {choice==="APPROVED"&&<div className="notice" aria-label="Mortgage product rate confirmation">
    <strong>Mortgage product interest-rate confirmation</strong>
    <p className="core-note">AL-Core uses a fixed development mortgage rate of <strong>{displayRate(DEVELOPMENT_MORTGAGE_RATE_BPS)}</strong> ({DEVELOPMENT_MORTGAGE_RATE_BPS} basis points). This backend-defined rate cannot be edited here and is not a calculated APRC or a personalised mortgage offer.</p>
    <p className="core-note">Requested mortgage: <strong>{application.requestedLoanAmount!=null?new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR"}).format(application.requestedLoanAmount):"Unavailable"}</strong> · Requested term: <strong>{application.requestedMortgageTerm} years</strong></p>
    <label className="human-decision-confirm"><input type="checkbox" checked={rateConfirmed} disabled={busy} onChange={e=>setRateConfirmed(e.target.checked)}/>
     <span>I have reviewed and confirm the fixed {displayRate(DEVELOPMENT_MORTGAGE_RATE_BPS)} mortgage product rate for this approval.</span></label>
   </div>}
   <p className="core-note">Review the actual AI recommendation and credit memorandum shown above; the fictional example preview does not count as evidence.</p>
   <label className="human-decision-confirm"><input type="checkbox" checked={confirmed} disabled={!choice||busy} onChange={e=>setConfirmed(e.target.checked)}/>
    <span>I have reviewed the live AI underwriting recommendation, reasoning and credit memorandum, and understand this is an independent human decision.</span></label>
   <button className="btn primary" type="button" disabled={!choice||!confirmed||busy||(choice==="APPROVED"&&!rateConfirmed)} onClick={()=>void submit()}>
    {busy?"Saving decision…":choice==="REJECTED"?"Confirm and reject":choice==="APPROVED"?"Confirm and approve":"Select a decision"}
   </button>
  </div>}
  {error&&<p className="auth-error" role="alert">{error}</p>}
 </section>;
}
