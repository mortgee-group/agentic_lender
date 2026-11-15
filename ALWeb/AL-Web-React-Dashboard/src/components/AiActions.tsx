import {useState} from "react";
import type {PortalRole} from "../types/roles";
import {requestCoreAssessment,requestCoreUnderwriting,type AssessmentResponse,type UnderwritingResponse} from "../services/api";
import AiResults,{exampleAIResponse} from "./AiResults";

type Task="assess"|"underwrite";
export default function AiActions({applicationId,role,onUpdated}:{
 applicationId:string;role:PortalRole;onUpdated:()=>void;
}){
 const canAssess=role==="broker"||role==="underwriter";
 const canUnderwrite=role==="underwriter";
 const [pending,setPending]=useState<Task|null>(null);
 const [working,setWorking]=useState<Task|null>(null);
 const [assessment,setAssessment]=useState<AssessmentResponse|null>(null);
 const [decision,setDecision]=useState<UnderwritingResponse|null>(null);
 const [error,setError]=useState("");
 const [notice,setNotice]=useState("");
 const [showExample,setShowExample]=useState(false);
 const run=async(task:Task)=>{
  setPending(null);setWorking(task);setError("");setNotice("");
  try{
   if(task==="assess"){
    const result=await requestCoreAssessment(applicationId);
    setAssessment(result);
    setNotice("Property valuation completed. AL-Core saved a valuation record.");
   }else{
    const result=await requestCoreUnderwriting(applicationId);
    setDecision(result);
    setNotice("AI underwriting completed. AL-Core saved the decision and updated the application.");
   }
   onUpdated();
  }catch(e){
   setError(e instanceof Error?e.message:"The AI service request failed.");
  }finally{setWorking(null);}
 };
 if(!canAssess)return null;
 return <section className="panel pad section ai-live-actions" aria-label="Live AI processing">
  <span className="eyebrow">AL-Core / AL-AI · Development</span>
  <h2>AI assessment and underwriting</h2>
  <p className="core-note">These actions contact AL-AI through AL-Core and may save results in the shared Firestore project. Use fictional test records only. AI outcomes are experimental, not lending decisions.</p>
  <div className="live-detail-actions ai-action-buttons">
   <button type="button" className="btn secondary" disabled={Boolean(working||pending)} onClick={()=>setPending("assess")}>
    {working==="assess"?"Assessing…":"Run property assessment"}</button>
   {canUnderwrite&&<button type="button" className="btn secondary" disabled={Boolean(working||pending)} onClick={()=>setPending("underwrite")}>
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
  {showExample&&<AiResults example assessment={exampleAIResponse.assessment} decision={exampleAIResponse.decision}/>}
  {(assessment||decision)&&<AiResults assessment={assessment} decision={decision}/>}

 </section>;
}
