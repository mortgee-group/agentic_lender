import {useEffect,useState} from "react";
import {ApiError,getCoreApplication,type CoreApplication} from "../services/api";
import type {PortalRole} from "../types/roles";

const euro=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const money=(n:number|null|undefined)=>typeof n==="number"&&Number.isFinite(n)?euro.format(n):"Not available";
const percent=(n:number|null|undefined)=>typeof n==="number"&&Number.isFinite(n)?n.toFixed(1)+"%":"Not available";
const date=(n:string|null|undefined)=>n?n.slice(0,10):"Not available";
const label=(s:string|null|undefined)=>s?s.replace(/_/g," ").toLowerCase().replace(/\b\w/g,x=>x.toUpperCase()):"Not available";
const num=(n:number|null|undefined)=>typeof n==="number"&&Number.isFinite(n)?n:null;

function DataRow({name,value,note}:{name:string;value:string;note?:string}){
 return <div className="collateral-row"><span>{name}{note&&<small>{note}</small>}</span><strong>{value}</strong></div>;
}

/** Real, read-only dossier. Do not call /assess or /underwrite here:
 * those endpoints can have persistent side effects.
 */
export default function CollateralAssessment({applicationId,role,onBack,onOpenLive}:{applicationId:string;role:PortalRole;onBack:()=>void;onOpenLive:()=>void}){
 const [record,setRecord]=useState<CoreApplication|null>(null);
 const [status,setStatus]=useState<"loading"|"loaded"|"error">("loading");
 const [error,setError]=useState("");
 const [refresh,setRefresh]=useState(0);
 useEffect(()=>{
  let active=true;
  setStatus("loading");setError("");setRecord(null);
  getCoreApplication(applicationId).then(a=>{
   if(active){setRecord(a);setStatus("loaded");}
  }).catch((e:unknown)=>{
   if(!active)return;
   setStatus("error");
   setError(e instanceof ApiError&&e.status===403?"AL-Core denied access to this application (HTTP 403).":
    e instanceof ApiError&&e.status===404?"The application was not found (HTTP 404).":
    e instanceof Error?e.message:"Unable to retrieve application.");
  });
  return()=>{active=false;};
 },[applicationId,refresh]);
 const a=record;
 const loan=a?num(a.requestedLoanAmount):null;
 const asking=a?num(a.sellerAskingPrice):null;
 const deposit=a?num(a.deposit):null;
 const impliedLoan=asking!==null&&deposit!==null?asking-deposit:null;
 const indicativeLtv=loan!==null&&asking!==null&&asking>0?loan/asking*100:null;
 const depositPct=deposit!==null&&asking!==null&&asking>0?deposit/asking*100:null;
 return <section className="page active collateral-dossier">
  <div className="flex-between"><div>
   <span className="eyebrow">{role==="broker"?"Assigned client file":"Underwriter collateral review"} · Live AL-Core</span>
   <h1 className="page-title">Loan File & Collateral Assessment</h1>
   <p className="subtitle">Application {applicationId}</p></div>
   <div className="live-detail-actions">
    <button type="button" className="btn secondary" onClick={onBack}>← Back to applications</button>
    <button type="button" className="btn secondary" onClick={()=>setRefresh(i=>i+1)} disabled={status==="loading"}>Refresh record</button>
   </div>
  </div>
  {status==="loading"&&<div className="panel pad" role="status">Retrieving authorised loan file from AL-Core…</div>}
  {status==="error"&&<div className="panel pad auth-error" role="alert">{error}</div>}
  {status==="loaded"&&a&&<>
   <div className="notice"><strong>Read-only Firestore dossier.</strong> Values below come from an application returned by AL-Core. This page never triggers an AI assessment, changes a loan status, or records a lending decision. Any displayed calculations are clearly identified as indicative.</div>
   <div className="grid3 portal-metrics">
    <div className="panel stat"><div className="label">Requested mortgage</div><div className="value collateral-metric">{money(loan)}</div><div className="mini">Application record</div></div>
    <div className="panel stat"><div className="label">Estimated LTV</div><div className="value collateral-metric">{percent(a.estimatedLTV)}</div><div className="mini">Stored AL-Core assessment</div></div>
    <div className="panel stat"><div className="label">Risk tier</div><div className="value collateral-metric">{a.riskTier??"Not assessed"}</div><div className="mini">Approval likelihood: {a.approvalLikelihood??"Not assessed"}</div></div>
   </div>
   <div className="detail-columns section">
    <div className="panel pad"><h2>Collateral & valuation</h2>
     <DataRow name="Seller asking price" value={money(asking)}/>
     <DataRow name="ML property valuation" value="Not available" note="The current application read API does not return the predicted property value"/>
     <DataRow name="Asking price vs ML valuation" value="Not available" note="Requires the actual valuation amount; no estimate is fabricated"/>
     <DataRow name="Stored estimated LTV" value={percent(a.estimatedLTV)} note="Assessment-derived value if populated"/>
     <DataRow name="Indicative asking-price LTV" value={percent(indicativeLtv)} note="Requested loan ÷ asking price; not the ML-based LTV"/>
     <DataRow name="Deposit as % of asking price" value={percent(depositPct)} note="Calculated from application figures"/>
     <p className="core-note">The asking price is not a verified independent valuation. Stored estimated LTV may use another valuation basis and therefore differ from asking-price LTV.</p>
    </div>
    <div className="panel pad"><h2>Borrower affordability</h2>
     <DataRow name="Annual household gross income" value={money(a.householdAnnualGrossIncome)}/>
     <DataRow name="Existing debt liabilities" value={money(a.existingDebtLiabilities)} note="As supplied by application"/>
     <DataRow name="Requested mortgage" value={money(loan)}/>
     <DataRow name="Requested mortgage term" value={a.requestedMortgageTerm+" years"}/>
     <DataRow name="Deposit" value={money(deposit)}/>
     <DataRow name="Asking price less deposit" value={money(impliedLoan)} note="Arithmetic cross-check; not a confirmed loan amount"/>
     <DataRow name="Backend DTI breakdown" value="Not available" note="No authenticated DTI detail fields in the current application read contract"/>
     <p className="core-note">A correct mortgage debt-service ratio requires a rate, repayment schedule and validated existing debt payments. The record alone is insufficient to state final affordability.</p>
    </div>
    <div className="panel pad"><h2>Property dossier</h2>
     <DataRow name="County" value={a.property?.county??"Not available"}/>
     <DataRow name="Property type" value={label(a.property?.property_type)}/>
     <DataRow name="Floor area" value={num(a.property?.floor_area)===null?"Not available":a.property.floor_area+" m²"}/>
     <DataRow name="Bedrooms" value={a.property?.bedrooms?.toString()??"Not available"}/>
     <DataRow name="Bathrooms" value={a.property?.bathrooms?.toString()??"Not available"}/>
     <DataRow name="Year built" value={a.property?.year_built?.toString()??"Not available"}/>
     <DataRow name="BER" value={a.property?.ber_rating??"Not available"}/>
    </div>
    <div className="panel pad"><h2>Application & assignment</h2>
     <DataRow name="Application ID" value={a.applicationId}/>
     <DataRow name="Lifecycle status" value={a.lifecycleStatus}/>
     <DataRow name="Submitted" value={date(a.submittedAt)}/>
     <DataRow name="Updated" value={date(a.updatedAt)}/>
     <DataRow name="Borrower UID" value={a.borrowerId}/>
     <DataRow name="Assigned broker UID" value={a.brokerId??"Not assigned"}/>
     <DataRow name="Approval likelihood" value={a.approvalLikelihood??"Not assessed"}/>
    </div>
   </div>
   <div className="panel pad section"><h2>Assessment & decision workflow</h2>
    <p>This is the read-only collateral dossier. The existing Live Application Details view provides the supported role-specific assessment and underwriting actions, and the human decision workflow when merged.</p>
    <button type="button" className="btn primary" onClick={onOpenLive}>Open live application workflow</button>
   </div>
  </>}
 </section>;
}
