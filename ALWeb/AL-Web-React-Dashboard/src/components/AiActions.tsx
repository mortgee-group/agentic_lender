import {useState} from "react";
import type {PortalRole} from "../types/roles";
import type {CoreApplication} from "../services/api";
import {requestCoreAssessment,requestCoreUnderwriting,type AssessmentResponse,type UnderwritingResponse} from "../services/api";
import AiResults,{exampleAIResponse} from "./AiResults";

type Task="assess"|"underwrite";
export default function AiActions({applicationId,lifecycleStatus,role,onUpdated,onUnderwritingCompleted,underwritingResult}:{
 applicationId:string;lifecycleStatus:CoreApplication["lifecycleStatus"];role:PortalRole;onUpdated:()=>void;
 onUnderwritingCompleted:(response:UnderwritingResponse)=>void;underwritingResult:UnderwritingResponse|null;
}){
 const canAssess=role==="broker";
 const canUnderwrite=role==="underwriter";
 const [pending,setPending]=useState<Task|null>(null);
 const [working,setWorking]=useState<Task|null>(null);
 const [assessment,setAssessment]=useState<AssessmentResponse|null>(null);
 const [error,setError]=useState("");
 const [notice,setNotice]=useState("");
 const [showExample,setShowExample]=useState(false);
 const run=async(task:Task)=>{
  if(lifecycleStatus!=="APPLIED" || (task==="assess"&&!canAssess) || (task==="underwrite"&&!canUnderwrite))return;
  setPending(null);setWorking(task);setError("");setNotice("");
  try{
   if(task==="assess"){
    const result=await requestCoreAssessment(applicationId);
    setAssessment(result);
    setNotice("Property valuation completed. AL-Core saved a valuation record.");
   }else{
    const result=await requestCoreUnderwriting(applicationId);
    onUnderwritingCompleted(result);
    setNotice("AI underwriting completed. AL-Core saved the decision and updated the application.");
   }
   onUpdated();
  }catch(e){
   setError(e instanceof Error?e.message:"The AI service request failed.");
  }finally{setWorking(null);}
 };
 if(!canAssess&&!canUnderwrite)return null;
 return <section className="panel pad section ai-live-actions" aria-label="Live AI processing">
  <span className="eyebrow">AL-Core / AL-AI · Development</span>
  <h2>AI assessment and underwriting</h2>
  <p className="core-note">The assigned Broker triggers property valuation and affordability assessment. The Underwriter then triggers AI underwriting, reviews the recommendation and makes a separate human decision. Actions may save results to Firestore. Use fictional test records only.</p>
  {lifecycleStatus!=="APPLIED"&&<p className="core-note">Application status: <strong>{lifecycleStatus}</strong>. New assessment and underwriting runs are unavailable at this stage.</p>}
  {canUnderwrite&&lifecycleStatus==="APPLIED"&&<p className="core-note">A property valuation must be completed before AI underwriting. AL-Core enforces this prerequisite; if a valuation is missing the request will be rejected.</p>}
  <div className="live-detail-actions ai-action-buttons">
   {canAssess&&<button type="button" className="btn secondary" disabled={lifecycleStatus!=="APPLIED"||Boolean(working||pending)} onClick={()=>setPending("assess")}>
    {working==="assess"?"Assessing…":"Run property assessment"}</button>}
   {canUnderwrite&&<button type="button" className="btn secondary" disabled={lifecycleStatus!=="APPLIED"||Boolean(working||pending)} onClick={()=>setPending("underwrite")}>
    {working==="underwrite"?"Underwriting…":"Run AI underwriting"}</button>}
   <button type="button" className="btn secondary" onClick={()=>setShowExample(value=>!value)}>
    {showExample?"Hide example preview":"Preview example results (offline)"}
   </button>
  </div>
  {pending&&<div className="core-register" role="group" aria-label="Confirm AI action">
   <p><strong>Confirm:</strong> {pending==="assess"?"Run a valuation and affordability assessment":"Run full AI underwriting"} for application <code>{applicationId}</code>? This will call the deployed backend and can write results to Firestore.</p>
   <div className="live-detail-actions">
    <button type="button" className="btn primary" onClick={()=>void run(pending)}>Confirm and run</button>
    <button type="button" className="btn secondary" onClick={()=>setPending(null)}>Cancel</button>
   </div>
  </div>}
  {working&&<p role="status">AL-Core is processing the {working==="assess"?"assessment":"underwriting"} request…</p>}
  {error&&<p className="auth-error" role="alert">{error}</p>}
  {notice&&<p role="status" className="core-note">{notice}</p>}
  {underwritingResult&&<p role="status" className="core-note"><strong>Live AI underwriting results are available below.</strong> Review the recommendation, rationale, conditions, agent findings and credit memo before making a human decision.</p>}
  {showExample&&<AiResults example assessment={exampleAIResponse.assessment} decision={exampleAIResponse.decision}/>}
  {(assessment||underwritingResult)&&<AiResults assessment={assessment} decision={underwritingResult}/>}

 </section>;
}
