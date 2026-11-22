import {useEffect,useState} from "react";
import {ApiError,getCoreApplication,getSavedCoreUnderwriting,displayRate,type CoreApplication,type UnderwritingResponse} from "../services/api";
import AiActions from "../components/AiActions";
import HumanDecision from "../components/HumanDecision";
import type {PortalRole} from "../types/roles";

const euro=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const amount=(n:number|null|undefined)=>typeof n==="number"&&Number.isFinite(n)?euro.format(n):"—";
const percent=(n:number|null|undefined)=>typeof n==="number"&&Number.isFinite(n)?`${n.toFixed(1)}%`:"Not assessed";
const date=(s:string|null|undefined)=>s?s.slice(0,10):"—";
const propertyType=(type:string)=>type.replace(/_/g," ").toLowerCase().replace(/\b\w/g,letter=>letter.toUpperCase());

export default function LiveApplicationDetail({applicationId,role,onBack}:{
 applicationId:string;role:PortalRole;onBack:()=>void;
}){
 const [record,setRecord]=useState<CoreApplication|null>(null);
 const [status,setStatus]=useState<"loading"|"loaded"|"error">("loading");
 const [error,setError]=useState("");
 const [reload,setReload]=useState(0);
 const [underwritingResult,setUnderwritingResult]=useState<UnderwritingResponse|null>(null);
 const [aiStatus,setAiStatus]=useState<"idle"|"loading"|"loaded"|"missing"|"error">("idle");
 const [aiError,setAiError]=useState("");
 const [aiRefresh,setAiRefresh]=useState(0);
 useEffect(()=>{
  let active=true;setError("");setStatus("loading");
  getCoreApplication(applicationId).then(item=>{
   if(!active)return;
   setRecord(item);setStatus("loaded");
  }).catch((e:unknown)=>{
   if(!active)return;
   setError(e instanceof ApiError&&e.status===403
    ?"Access denied by AL-Core (HTTP 403). This application is not accessible to your verified backend role."
    :e instanceof ApiError&&e.status===404?"Application not found (HTTP 404)."
    :e instanceof Error?e.message:"Unable to retrieve application");
   setStatus("error");
  });
  return ()=>{active=false;};
 },[applicationId,reload]);
 useEffect(()=>{
  // Fetch persisted AI evidence only for a signed-in Underwriter and an
  // application that has entered the AI deliberation or later lifecycle.
  if(role!=="underwriter"||!record||record.applicationId!==applicationId||record.lifecycleStatus==="APPLIED"){
   setAiStatus("idle");setAiError("");return;
  }
  let active=true;
  setAiStatus("loading");setAiError("");
  getSavedCoreUnderwriting(applicationId).then(result=>{
   if(!active)return;
   // A saved record must belong to the currently selected application.
   if(result?.applicationId!==applicationId||!result.credit_memo||!result.decision){
    setUnderwritingResult(null);setAiStatus("error");
    setAiError("AL-Core returned incomplete or mismatched AI evidence. Human decisions remain disabled.");
    return;
   }
   setUnderwritingResult(result);setAiStatus("loaded");
  }).catch((cause:unknown)=>{
   if(!active)return;
   // A 404 means no saved decision, but it does not mean the application
   // itself is missing (the application GET already succeeded).
   setAiStatus(cause instanceof ApiError&&cause.status===404?"missing":"error");
   setAiError(cause instanceof ApiError&&cause.status===404
    ?"No saved AI underwriting decision was found for this application."
    :cause instanceof ApiError&&cause.status===403
    ?"AL-Core denied access to the saved AI decision (HTTP 403)."
    :cause instanceof Error?cause.message:"Unable to retrieve saved AI underwriting results.");
   // A result from a just-completed POST is still usable in this session.
   setUnderwritingResult(current=>current?.applicationId===applicationId?current:null);
  });
  return()=>{active=false;};
 },[applicationId,role,record?.applicationId,record?.lifecycleStatus,aiRefresh]);
 const onUnderwritingCompleted=(result:UnderwritingResponse)=>{
  // The subsequent application refresh will verify the lifecycle.
  if(result.applicationId!==applicationId)return;
  setUnderwritingResult(result);
  setAiStatus("loaded");setAiError("");
 };
 const field=(label:string,value:string|number)=><div className="detail-row" key={label}>
  <span>{label}</span><strong>{value}</strong>
 </div>;
 return <section className="page active live-detail">
  <div className="flex-between"><div>
   <span className="eyebrow">AL-Core · Firestore record</span>
   <h1 className="page-title">Live application details</h1>
   <p className="subtitle">Application ID: {applicationId}</p>
  </div><div className="live-detail-actions">
   <button type="button" className="btn secondary" onClick={onBack}>← Back to applications</button>
   <button type="button" className="btn secondary" onClick={()=>setReload(n=>n+1)} disabled={status==="loading"}>Refresh</button>
  </div></div>
  {status==="loading"&&<div className="panel pad" role="status">Loading application from AL-Core…</div>}
  {status==="error"&&<div className="panel pad auth-error" role="alert">{error}</div>}
  {status==="loading"&&record&&<p role="status" className="core-note">Refreshing application data…</p>}
  {(status==="loaded"||status==="loading")&&record&&<div className="live-detail-sections">
   <div className="notice"><strong>Live test record.</strong> This information was retrieved from AL-Core, not the fictional UI dataset. No changes are made by viewing this page.</div>
   <div className="detail-columns">
    <div className="panel pad"><h2>Application status</h2>
     {field("Application ID",record.applicationId)}
     {field("Lifecycle status",record.lifecycleStatus)}
     {field("Submitted",date(record.submittedAt))}
     {field("Created",date(record.createdAt))}
     {field("Updated",date(record.updatedAt))}
    </div>
    <div className="panel pad"><h2>Assessment</h2>
     {field("Estimated LTV",percent(record.estimatedLTV))}
     {field("Risk tier",record.riskTier??"Not assessed")}
     {field("Approval likelihood",record.approvalLikelihood??"Not assessed")}
     {field("Human decision",record.underwriterDecision??"Awaiting review")}
     {record.decidedAt&&field("Human decision date",new Date(record.decidedAt).toLocaleString("en-IE"))}
    </div>
    <div className="panel pad"><h2>Financial details</h2>
     {field("Household gross income",amount(record.householdAnnualGrossIncome))}
     {field("Existing debt liabilities",amount(record.existingDebtLiabilities))}
     {field("Property asking price",amount(record.sellerAskingPrice))}
     {field("Deposit",amount(record.deposit))}
     {field("Requested mortgage",amount(record.requestedLoanAmount))}
     {record.approvedLoanAmount!=null&&field("Approved mortgage",amount(record.approvedLoanAmount))}
     {record.approvedAprBasisPoints!=null&&field("Approved mortgage product rate",displayRate(record.approvedAprBasisPoints))}
     {record.underwriterDecision==="APPROVED"&&field("Product rate confirmed",record.interestRateConfirmed?"Yes":"Not confirmed")}
     {field("Mortgage term",`${record.requestedMortgageTerm} years`)}
    </div>
    <div className="panel pad"><h2>Property details</h2>
     {field("County",record.property.county)}
     {field("Property type",propertyType(record.property.property_type))}
     {field("Floor area",`${record.property.floor_area} m²`)}
     {field("Bedrooms",record.property.bedrooms)}
     {field("Bathrooms",record.property.bathrooms)}
     {field("Year built",record.property.year_built)}
     {field("BER rating",record.property.ber_rating)}
    </div>
   </div>
   {role==="underwriter"&&record.lifecycleStatus!=="APPLIED"&&<div className="panel pad section" aria-label="Saved AI decision status">
    <h2>Saved AI underwriting evidence</h2>
    {aiStatus==="loading"&&<p role="status">Retrieving saved AI decision from AL-Core…</p>}
    {aiStatus==="loaded"&&<p role="status" className="core-note">Saved AI underwriting evidence loaded. Review the findings below before confirming a human decision.</p>}
    {(aiStatus==="missing"||aiStatus==="error")&&<p role="alert" className="auth-error">{aiError} The human decision form will remain disabled unless genuine evidence is available.</p>}
    <button type="button" className="btn secondary" disabled={aiStatus==="loading"} onClick={()=>setAiRefresh(n=>n+1)}>Reload AI evidence</button>
   </div>}
   <AiActions applicationId={applicationId} lifecycleStatus={record.lifecycleStatus} role={role} onUpdated={()=>setReload(n=>n+1)} underwritingResult={underwritingResult} onUnderwritingCompleted={onUnderwritingCompleted}/>
   <HumanDecision application={record} role={role} aiResultsAvailable={status==="loaded"&&record.lifecycleStatus==="AI_DELIBERATED"&&underwritingResult?.applicationId===record.applicationId&&Boolean(underwritingResult.credit_memo&&underwritingResult.decision)} onUpdated={()=>setReload(n=>n+1)}/>
   <div className="panel pad"><h2>Assignment</h2>
    {field("Borrower UID",record.borrowerId)}
    {field("Broker UID",record.brokerId??"Not assigned")}
    <p className="core-note">AL-Core checks ownership and role permissions before returning this record. UI roles do not grant access.</p>
   </div>
  </div>}
 </section>;
}
