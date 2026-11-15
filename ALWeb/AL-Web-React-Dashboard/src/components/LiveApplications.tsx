import {useCallback,useEffect,useState} from "react";
import {ApiError,getCoreApplications,type CoreApplication} from "../services/api";
import type {AuthUser} from "../services/auth";

const currency = new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const money = (n:number|null) => typeof n==="number"&&Number.isFinite(n)?currency.format(n):"—";
const percentage = (n:number|null) => typeof n==="number"&&Number.isFinite(n)?`${n.toFixed(1)}%`:"—";
const riskDescription=(tier:CoreApplication["riskTier"])=>{
 if(!tier)return "Not assessed";
 const rating=Number(tier.slice(-1));
 return `${tier} (${rating===1?"lowest":rating===5?"highest":"intermediate"} risk)`;
};
const date = (value:string|null) => value?value.slice(0,10):"—";

export default function LiveApplications({user,onOpenApplication,dashboard=false}:{user:AuthUser;onOpenApplication:(id:string)=>void;dashboard?:boolean}){
 const [records,setRecords]=useState<CoreApplication[]>([]);
 const [status,setStatus]=useState<"loading"|"loaded"|"error">("loading");
 const [error,setError]=useState("");
 const [revision,setRevision]=useState(0);
 const refresh=useCallback(()=>setRevision(v=>v+1),[]);

 useEffect(()=>{
  let active=true;
  setStatus("loading");setError("");setRecords([]);
  getCoreApplications().then(data=>{
   if(!active)return;
   setRecords(data);setStatus("loaded");
  }).catch((cause:unknown)=>{
   if(!active)return;
   setStatus("error");
   setError(cause instanceof ApiError&&cause.status===403
    ? "AL-Core denied application access (HTTP 403). Check that this Firebase user has the correct Firestore profile and backend role."
    : cause instanceof Error?cause.message:"Unable to retrieve applications");
  });
  return ()=>{active=false;};
 },[user.uid,revision]);

 const awaiting=records.filter(r=>r.lifecycleStatus==="APPLIED"||r.lifecycleStatus==="AI_DELIBERATED").length;
 const assessed=records.filter(r=>r.riskTier!==null||r.lifecycleStatus!=="APPLIED").length;
 const highRisk=records.filter(r=>r.riskTier==="Tier 4"||r.riskTier==="Tier 5").length;
 const scope=user.role==="borrower"?"My applications":user.role==="broker"?"Assigned client applications":"Underwriting portfolio";
 return <section className="live-workspace" aria-label="Live AL-Core applications">
  {dashboard&&<>
   <div className="flex-between live-workspace-heading">
    <div><span className="eyebrow">Live AL-Core data</span>
     <h1 className="page-title">{user.role==="borrower"?"Borrower Dashboard":user.role==="broker"?"Broker Dashboard":"Underwriter Dashboard"}</h1>
     <p className="subtitle">Application information retrieved from Firestore through AL-Core for your verified account.</p>
    </div><span className="status">Live API</span>
   </div>
   {status==="loaded"&&<div className="grid3 portal-metrics" role="status" aria-label="Live dashboard metrics">
    <div className="panel stat"><div className="label">{scope}</div><div className="value">{records.length}</div><div className="mini">Accessible Firestore applications</div></div>
    <div className="panel stat"><div className="label">Awaiting final decision</div><div className="value">{awaiting}</div><div className="mini">Applied or AI deliberated</div></div>
    <div className="panel stat"><div className="label">{user.role==="borrower"?"Assessed applications":"High-risk files"}</div>
     <div className="value">{user.role==="borrower"?assessed:highRisk}</div>
     <div className="mini">{user.role==="borrower"?"AI assessment recorded or status advanced":"Risk tier 4–5"}</div>
    </div>
   </div>}
  </>}
  <div className="panel live-applications section">
  <div className="table-head">
   <div><h2>{dashboard?scope:scope}</h2>
    <p className="live-applications-caption">Read-only records from AL-Core / Firestore · Permissions enforced by the backend</p></div>
   <button type="button" className="btn secondary" onClick={refresh} disabled={status==="loading"}>
    {status==="loading"?"Loading…":"Refresh"}
   </button>
  </div>
  {status==="loading"&&<div className="empty" role="status">Retrieving applications from AL-Core…</div>}
  {status==="error"&&<div className="live-applications-error" role="alert">{error}</div>}
  {status==="loaded"&&records.length===0&&
   <div className="empty" role="status">No live applications are available for this account. This is an actual empty API result, not demonstration data.</div>}
  {status==="loaded"&&records.length>0&&<>
   <p className="live-applications-count" role="status">{records.length} application{records.length===1?"":"s"} returned by AL-Core</p>
   <div className="table-wrap"><table>
    <thead><tr><th>Application ID</th><th>County</th><th>Requested</th><th>LTV</th><th>Risk</th><th>Approval likelihood</th><th>Status</th><th>Submitted</th><th>Details</th></tr></thead>
    <tbody>{records.map(item=><tr key={item.applicationId}>
     <td className="strong">{item.applicationId}</td>
     <td>{item.property?.county??"—"}</td>
     <td>{money(item.requestedLoanAmount)}</td>
     <td>{percentage(item.estimatedLTV)}</td>
     <td>{riskDescription(item.riskTier)}</td>
     <td>{item.approvalLikelihood??"Not assessed"}</td>
     <td>{item.lifecycleStatus}</td>
     <td>{date(item.submittedAt)}</td>
     <td><button type="button" className="link" onClick={()=>onOpenApplication(item.applicationId)}
      aria-label={`View live application ${item.applicationId}`}>View details →</button></td>
    </tr>)}</tbody>
   </table></div>
  </>}
  <p className="live-applications-footer">Records shown above come from AL-Core. Backend application ownership and assignment are checked by AL-Core. Synthetic AI Audit examples remain separate.</p>
 </div>
 </section>;
}
