import {useMemo,useState} from "react";
import {loanStatuses,riskTiers,type LoanApplication,type LoanStatus,type RiskTier} from "../data/sampleApplications";
import {euro,percent} from "../utils/loanCalculations";
import type {PortalRole} from "../types/roles";
type Props={applications:LoanApplication[];role:PortalRole;mode:"pipeline"|"files";onOpenFile:(id:string)=>void};
type StatusFilter=LoanStatus|"all";
type RiskFilter=RiskTier|"all";
type SortKey="submitted"|"ltv-high"|"ltv-low"|"amount-high";
export default function LoanPipeline({applications,role,mode,onOpenFile}:Props){
 const [search,setSearch]=useState("");
 const [status,setStatus]=useState<StatusFilter>("all");
 const [risk,setRisk]=useState<RiskFilter>("all");
 const [ltvBand,setLtvBand]=useState("all");
 const [sort,setSort]=useState<SortKey>("submitted");
 const filtered=useMemo(()=>{
  const text=search.trim().toLocaleLowerCase();
  return applications.filter(item=>{
   if(text&&!([item.id,item.borrower,item.county,item.propertyAddress].some(field=>field.toLocaleLowerCase().includes(text))))return false;
   if(status!=="all"&&item.status!==status)return false;
   if(risk!=="all"&&item.risk!==risk)return false;
   if(ltvBand==="under80"&&item.ltv>=80)return false;
   if(ltvBand==="80to90"&&(item.ltv<80||item.ltv>=90))return false;
   if(ltvBand==="90plus"&&item.ltv<90)return false;
   return true;
  }).sort((a,b)=>sort==="ltv-high"?b.ltv-a.ltv:sort==="ltv-low"?a.ltv-b.ltv:sort==="amount-high"?b.requestedAmount-a.requestedAmount:b.submittedDate.localeCompare(a.submittedDate));
 },[applications,search,status,risk,ltvBand,sort]);
 const reset=()=>{setSearch("");setStatus("all");setRisk("all");setLtvBand("all");setSort("submitted");};
 return <section className="page active">
  <div className="flex-between"><div><span className="eyebrow">{role==="broker"?"Assigned clients":"Underwriting portfolio"}</span>
   <h1 className="page-title">{mode==="pipeline"?"Loan Origination Pipeline":"Loan Files"}</h1>
   <p className="subtitle">{role==="broker"?"Only applications assigned to this demo broker appear.":"Review the complete fictional portfolio, grouped by risk and lifecycle."}</p>
  </div><span className="status demo">Demo data — no live loan decisions</span></div>
  <div className="panel pad pipeline-filters"><div className="flex-between"><h2>Filter applications</h2><button type="button" className="btn ghost" onClick={reset}>Reset filters</button></div>
   <div className="filter-grid">
    <div className="field"><label htmlFor="pipeline-search">Search borrower, file or location</label><input id="pipeline-search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="e.g. AL-1001 or Kildare"/></div>
    <div className="field"><label htmlFor="pipeline-status">Lifecycle status</label><select id="pipeline-status" value={status} onChange={e=>setStatus(e.target.value as StatusFilter)}><option value="all">All stages</option>{loanStatuses.map(s=><option key={s} value={s}>{s}</option>)}</select></div>
    <div className="field"><label htmlFor="pipeline-risk">Risk tier</label><select id="pipeline-risk" value={risk} onChange={e=>setRisk(e.target.value as RiskFilter)}><option value="all">All risks</option>{riskTiers.map(r=><option key={r} value={r}>{r}</option>)}</select></div>
    <div className="field"><label htmlFor="pipeline-ltv">Loan-to-value</label><select id="pipeline-ltv" value={ltvBand} onChange={e=>setLtvBand(e.target.value)}><option value="all">All LTVs</option><option value="under80">Below 80%</option><option value="80to90">80% to under 90%</option><option value="90plus">90% and above</option></select></div>
    <div className="field"><label htmlFor="pipeline-sort">Sort by</label><select id="pipeline-sort" value={sort} onChange={e=>setSort(e.target.value as SortKey)}><option value="submitted">Newest submitted</option><option value="ltv-high">Highest LTV</option><option value="ltv-low">Lowest LTV</option><option value="amount-high">Highest amount</option></select></div>
   </div>
  </div>
  <div className="panel section">
   <div className="table-head"><div><h2>Applications</h2><p style={{margin:0,fontSize:12}} aria-live="polite">Showing {filtered.length} of {applications.length} visible applications</p></div></div>
   <div className="table-wrap"><table><thead><tr><th>File</th><th>Borrower / County</th><th>Requested</th><th>LTV</th><th>Risk</th><th>Status</th><th>Submitted</th><th>Details</th></tr></thead><tbody>
    {filtered.map(item=><tr key={item.id}><td className="strong">{item.id}</td><td>{item.borrower}<div className="card-note">{item.county}</div></td><td>{euro(item.requestedAmount)}</td><td className="strong">{percent(item.ltv)}</td><td><span className={`risk ${item.risk.toLowerCase()}`}>{item.risk}</span></td><td>{item.status}</td><td>{item.submittedDate}</td><td><button type="button" className="link" onClick={()=>onOpenFile(item.id)} aria-label={`View file ${item.id}`}>View file →</button></td></tr>)}
   </tbody></table>{filtered.length===0&&<div className="empty">No applications match the selected filters. <button type="button" className="link" onClick={reset}>Clear filters</button></div>}</div>
  </div>
 </section>;
}
