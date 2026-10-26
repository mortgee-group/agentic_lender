import BackendStatus from "../components/BackendStatus";
import SummaryCard from "../components/SummaryCard";
import {visibleApplications,sampleApplications,type LoanApplication} from "../data/sampleApplications";
import {euro} from "../utils/loanCalculations";
import type {PortalRole} from "../types/roles";
import {roleLabel} from "../types/roles";
type Props={role:PortalRole;username:string;onOpenPipeline:()=>void;onOpenFile:(id:string)=>void;onOpenAudit:()=>void};
export default function RoleDashboard({role,username,onOpenPipeline,onOpenFile,onOpenAudit}:Props){
 const visible=visibleApplications(role,username,sampleApplications);
 const highRisk=visible.filter(a=>a.risk==="High").length;
 return <section className="page active portal-dashboard">
  <div className="flex-between"><div><span className="eyebrow">{roleLabel(role)} workspace</span>
   <h1 className="page-title">{role==="broker"?"Broker Dashboard":"Underwriter Dashboard"}</h1>
   <p className="subtitle">{role==="broker"?"Track your referred client loan applications.":"Review portfolio risk, decisions and underwriting activity."}</p>
  </div><span className="status demo">Fictional demo applications</span></div>
  <BackendStatus/>
  <div className="grid3 portal-metrics">
   <SummaryCard title={role==="broker"?"Assigned Applications":"Portfolio Applications"} icon="▤" value={visible.length} note="Example applications"/>
   <SummaryCard title="Awaiting Decision" icon="⌁" value={visible.filter(a=>a.status==="Applied"||a.status==="AI Deliberated").length} note="Demo statuses"/>
   <SummaryCard title={role==="broker"?"Average LTV":"High Risk Files"} icon="▦" value={role==="broker"?(visible.length?Math.round(visible.reduce((s,a)=>s+a.ltv,0)/visible.length)+"%":"—"):highRisk} note="Illustrative metrics only" compact/>
  </div>
  <div className="panel section"><div className="table-head"><div><h2>{role==="broker"?"My Client Applications":"Portfolio Overview"}</h2><p style={{fontSize:12,margin:0}}>Demonstration records; no real borrower information</p></div>
    <button className="btn ghost" type="button" onClick={onOpenPipeline}>Open pipeline →</button></div>
   <div className="table-wrap"><table><thead><tr><th>File</th><th>Borrower</th><th>Requested</th><th>LTV</th><th>Risk</th><th>Stage</th><th></th></tr></thead>
   <tbody>{visible.map((a:LoanApplication)=><tr key={a.id}><td className="strong">{a.id}</td><td>{a.borrower}<div className="card-note">{a.county}</div></td><td>{euro(a.requestedAmount)}</td><td>{a.ltv.toFixed(1)}%</td><td><span className={`risk ${a.risk.toLowerCase()}`}>{a.risk}</span></td><td>{a.status}</td><td><button className="link" type="button" onClick={()=>onOpenFile(a.id)}>View file →</button></td></tr>)}</tbody></table></div>
  </div>
  <div className="panel pad section"><h2>Upcoming modules</h2><p>{role==="broker"?"Assignment management and secure borrower communication will follow backend integration.":"AI deliberation, macro stress testing and settlement approvals will follow."}</p>
  {role==="underwriter"&&<button className="btn secondary" type="button" onClick={onOpenAudit}>View AI Audit placeholder →</button>}</div>
 </section>;
}
