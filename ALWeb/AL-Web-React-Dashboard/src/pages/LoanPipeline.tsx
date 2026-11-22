import {useEffect,useMemo,useState} from "react";
import {ApiError,getCoreApplications,type CoreApplication} from "../services/api";
import type {AuthUser} from "../services/auth";

const stages = [
 ["APPLIED","Applied"],["AI_DELIBERATED","AI deliberated"],
 ["UNDERWRITER_APPROVED","Underwriter approved"],["FUNDED","Funded"],["CLOSED","Closed"],
] as const;
type Stage=CoreApplication["lifecycleStatus"];
type Sort="newest"|"oldest"|"amount-high"|"amount-low"|"ltv-high"|"ltv-low";
const moneyFormat=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const numeric=(v:unknown):v is number=>typeof v==="number"&&Number.isFinite(v);
const money=(v:number|null)=>numeric(v)?moneyFormat.format(v):"—";
const pct=(v:number|null)=>numeric(v)?v.toFixed(1)+"%":"—";
const stageLabel=(v:Stage)=>stages.find(([key])=>key===v)?.[1]??v;
const submitted=(a:CoreApplication)=>a.submittedAt||a.createdAt||"";
const time=(a:CoreApplication)=>{const n=Date.parse(submitted(a));return Number.isFinite(n)?n:0;};
const compareNumber=(a:number|null,b:number|null,descending:boolean)=>{
 if(!numeric(a))return numeric(b)?1:0;
 if(!numeric(b))return -1;
 return descending?b-a:a-b;
};
const riskClass=(v:CoreApplication["riskTier"])=>v==="Tier 4"||v==="Tier 5"?"high":v==="Tier 3"?"medium":v==="Tier 1"||v==="Tier 2"?"low":"";

export default function LoanPipeline({user,onOpenFile}:{user:AuthUser;onOpenFile:(id:string)=>void}){
 const [applications,setApplications]=useState<CoreApplication[]>([]);
 const [load,setLoad]=useState<"loading"|"loaded"|"error">("loading");
 const [error,setError]=useState("");
 const [revision,setRevision]=useState(0);
 const [search,setSearch]=useState("");
 const [stage,setStage]=useState("all");
 const [risk,setRisk]=useState("all");
 const [likelihood,setLikelihood]=useState("all");
 const [ltv,setLtv]=useState("all");
 const [sort,setSort]=useState<Sort>("newest");
 useEffect(()=>{
  let active=true;
  setLoad("loading");setError("");setApplications([]);
  getCoreApplications().then(items=>{if(active){setApplications(items);setLoad("loaded");}})
   .catch((e:unknown)=>{if(active){setLoad("error");setError(e instanceof ApiError&&e.status===403
    ?"AL-Core denied access. Check your authenticated backend profile and permissions."
    :e instanceof Error?e.message:"Unable to retrieve applications.");}});
  return()=>{active=false;};
 },[user.uid,revision]);
 const filtered=useMemo(()=>{
  const q=search.trim().toLowerCase();
  return applications.filter(a=>{
   if(q&&![a.applicationId,a.borrowerId,a.brokerId,a.property?.county,a.property?.property_type].some(v=>v?.toLowerCase().includes(q)))return false;
   if(stage!=="all"&&a.lifecycleStatus!==stage)return false;
   if(risk==="unassessed"?a.riskTier!=null:risk!=="all"&&a.riskTier!==risk)return false;
   if(likelihood==="unassessed"?a.approvalLikelihood!=null:likelihood!=="all"&&a.approvalLikelihood!==likelihood)return false;
   const v=a.estimatedLTV;
   if(ltv==="unknown")return !numeric(v);
   if(ltv!=="all"&&!numeric(v))return false;
   if(numeric(v)){
    if(ltv==="under80"&&v>=80)return false;
    if(ltv==="80to90"&&(v<80||v>=90))return false;
    if(ltv==="90plus"&&v<90)return false;
   }
   return true;
  }).sort((a,b)=>{
   const cmp=sort==="amount-high"?compareNumber(a.requestedLoanAmount,b.requestedLoanAmount,true):
    sort==="amount-low"?compareNumber(a.requestedLoanAmount,b.requestedLoanAmount,false):
    sort==="ltv-high"?compareNumber(a.estimatedLTV,b.estimatedLTV,true):
    sort==="ltv-low"?compareNumber(a.estimatedLTV,b.estimatedLTV,false):
    sort==="oldest"?time(a)-time(b):time(b)-time(a);
   return cmp||a.applicationId.localeCompare(b.applicationId);
  });
 },[applications,search,stage,risk,likelihood,ltv,sort]);
 const reset=()=>{setSearch("");setStage("all");setRisk("all");setLikelihood("all");setLtv("all");setSort("newest");};
 const total=applications.reduce((sum,a)=>sum+(numeric(a.requestedLoanAmount)?a.requestedLoanAmount:0),0);
 return <section className="page active live-pipeline" aria-label="Live loan origination pipeline">
  <div className="flex-between"><div><span className="eyebrow">Live AL-Core / Firestore</span>
   <h1 className="page-title">Loan Origination Pipeline</h1>
   <p className="subtitle">{user.role==="broker"?"Track applications assigned to your broker account.":"Review the underwriting portfolio and identify applications requiring attention."}</p>
  </div><button type="button" className="btn secondary" disabled={load==="loading"} onClick={()=>setRevision(n=>n+1)}>{load==="loading"?"Loading…":"Refresh applications"}</button></div>
  {load==="loading"&&<div className="panel pad" role="status">Retrieving applications from AL-Core…</div>}
  {load==="error"&&<div className="panel pad auth-error" role="alert">{error}</div>}
  {load==="loaded"&&<>
   <div className="grid3 portal-metrics">
    <div className="panel stat"><div className="label">Accessible applications</div><div className="value">{applications.length}</div><div className="mini">All records returned by AL-Core</div></div>
    <div className="panel stat"><div className="label">Total requested mortgages</div><div className="value">{money(total)}</div><div className="mini">Known loan amounts across all stages</div></div>
    <div className="panel stat"><div className="label">High risk applications</div><div className="value">{applications.filter(a=>a.riskTier==="Tier 4"||a.riskTier==="Tier 5").length}</div><div className="mini">Tier 4 or Tier 5</div></div>
   </div>
   <section className="panel pad section"><h2>Application lifecycle</h2><p className="core-note">Counts reflect the entire accessible portfolio. Select a stage to filter the table.</p>
    <div className="pipeline-stages">{stages.map(([key,label])=><button key={key} type="button" className="pipeline-stage" aria-pressed={stage===key} onClick={()=>setStage(current=>current===key?"all":key)}><span>{label}</span><strong>{applications.filter(a=>a.lifecycleStatus===key).length}</strong></button>)}</div>
   </section>
   <div className="panel pad pipeline-filters"><div className="flex-between"><h2>Filter applications</h2><button type="button" className="btn ghost" onClick={reset}>Reset filters</button></div>
    <div className="live-pipeline-filters">
     <div className="field"><label htmlFor="pipeline-search">Search application, UID or county</label><input id="pipeline-search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Application ID, county, property type"/></div>
     <div className="field"><label htmlFor="pipeline-status">Lifecycle status</label><select id="pipeline-status" value={stage} onChange={e=>setStage(e.target.value)}><option value="all">All stages</option>{stages.map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></div>
     <div className="field"><label htmlFor="pipeline-risk">Risk tier</label><select id="pipeline-risk" value={risk} onChange={e=>setRisk(e.target.value)}><option value="all">All risks</option><option value="unassessed">Not assessed</option>{[1,2,3,4,5].map(n=><option key={n}>Tier {n}</option>)}</select></div>
     <div className="field"><label htmlFor="pipeline-likelihood">Approval likelihood</label><select id="pipeline-likelihood" value={likelihood} onChange={e=>setLikelihood(e.target.value)}><option value="all">All categories</option><option value="unassessed">Not assessed</option>{["High","Medium","Low"].map(v=><option key={v}>{v}</option>)}</select></div>
     <div className="field"><label htmlFor="pipeline-ltv">Estimated LTV</label><select id="pipeline-ltv" value={ltv} onChange={e=>setLtv(e.target.value)}><option value="all">All LTVs</option><option value="under80">Below 80%</option><option value="80to90">80% to under 90%</option><option value="90plus">90% and above</option><option value="unknown">Not available</option></select></div>
     <div className="field"><label htmlFor="pipeline-sort">Sort by</label><select id="pipeline-sort" value={sort} onChange={e=>setSort(e.target.value as Sort)}><option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="amount-high">Highest loan amount</option><option value="amount-low">Lowest loan amount</option><option value="ltv-high">Highest LTV</option><option value="ltv-low">Lowest LTV</option></select></div>
    </div>
   </div>
   <div className="panel section live-applications"><div className="table-head"><div><h2>Applications</h2><p className="live-applications-caption" role="status">Showing {filtered.length} of {applications.length} accessible applications</p></div></div>
    {filtered.length===0?<div className="empty">{applications.length===0?"No applications are available for this account.":<>No applications match the selected filters. <button className="link" type="button" onClick={reset}>Clear filters</button></>}</div>:
     <div className="table-wrap"><table><thead><tr><th>Application ID</th><th>County</th><th>Requested loan</th><th>LTV</th><th>Risk tier</th><th>Approval likelihood</th><th>Lifecycle</th><th>Submitted / created</th><th>Details</th></tr></thead>
      <tbody>{filtered.map(a=><tr key={a.applicationId}><td className="strong pipeline-id">{a.applicationId}</td><td>{a.property?.county??"—"}</td><td>{money(a.requestedLoanAmount)}</td><td>{pct(a.estimatedLTV)}</td><td><span className={"risk "+riskClass(a.riskTier)}>{a.riskTier??"Not assessed"}</span></td><td>{a.approvalLikelihood??"Not assessed"}</td><td>{stageLabel(a.lifecycleStatus)}</td><td>{submitted(a).slice(0,10)||"—"}</td><td><button className="link" type="button" aria-label={`View live application ${a.applicationId}`} onClick={()=>onOpenFile(a.applicationId)}>View details →</button></td></tr>)}</tbody></table></div>}
    <p className="live-applications-footer">Read-only view of backend-authorised records. Filters do not change loan status or assign brokers. Missing assessments are not treated as low risk. AI recommendations are not final lending decisions.</p>
   </div>
  </>}
 </section>;
}
