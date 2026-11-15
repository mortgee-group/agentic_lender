import {useState} from "react";
import type {PortalRole} from "../types/roles";
import {requestCoreAssessment,requestCoreUnderwriting,type AssessmentResponse,type UnderwritingResponse} from "../services/api";

type Task="assess"|"underwrite";
const money=(value:number)=>new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0}).format(value);
const percent=(value:number)=>`${value.toFixed(1)}%`;
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
  {assessment&&<div className="ai-live-results" aria-label="Valuation results">
   <h3>Valuation and affordability results</h3>
   <div className="detail-row"><span>Predicted property value</span><strong>{money(assessment.predicted_value)}</strong></div>
   <div className="detail-row"><span>Overvaluation</span><strong>{percent(assessment.overvaluation_pct)}</strong></div>
   <div className="detail-row"><span>Estimated LTV</span><strong>{percent(assessment.ltv)}</strong></div>
   <div className="detail-row"><span>Debt-to-income</span><strong>{percent(assessment.dti)}</strong></div>
   <div className="detail-row"><span>Stress-tested monthly repayment</span><strong>{money(assessment.stress_tested_repayment)}</strong></div>
   <div className="detail-row"><span>Maximum borrowing capacity</span><strong>{money(assessment.max_borrowing_capacity)}</strong></div>
  </div>}
  {decision&&<div className="ai-live-results" aria-label="AI underwriting results">
   <h3>AI underwriting output</h3>
   <div className="detail-row"><span>AI recommendation</span><strong>{decision.decision}</strong></div>
   <div className="detail-row"><span>Risk tier</span><strong>{decision.riskTier}</strong></div>
   <div className="detail-row"><span>Approval category</span><strong>{decision.approvalLikelihood}</strong></div>
   <div className="detail-row"><span>Estimated LTV</span><strong>{percent(decision.estimatedLTV)}</strong></div>
   <div className="detail-row"><span>DTI</span><strong>{percent(decision.DTI)}</strong></div>
   <h3>Reasoning</h3><p>{decision.rationale}</p>
   {decision.conditions?.length>0&&<><h3>Conditions</h3><ul>{decision.conditions.map((condition,i)=><li key={i}>{condition}</li>)}</ul></>}
   {decision.credit_memo&&<><h3>Credit memorandum</h3><p className="ai-credit-memo">{decision.credit_memo}</p></>}
   <p className="core-note">The full response also contains agent audit data. A structured audit screen will follow in the next development step.</p>
  </div>}
 </section>;
}
