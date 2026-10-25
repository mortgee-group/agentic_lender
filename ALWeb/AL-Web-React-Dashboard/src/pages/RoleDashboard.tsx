import BackendStatus from "../components/BackendStatus";
import SummaryCard from "../components/SummaryCard";
import {sampleApplications, type LoanApplication} from "../data/sampleApplications";
import type {PortalRole} from "../types/roles";
import {roleLabel} from "../types/roles";

type Props = {role:PortalRole; username:string; onOpenPipeline:()=>void; onOpenAudit:()=>void};
const euro=(n:number)=>new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0}).format(n);

export default function RoleDashboard({role,username,onOpenPipeline,onOpenAudit}:Props){
  const visible=role==="broker" ? sampleApplications.filter(a=>a.broker===username) : sampleApplications;
  const highRisk=visible.filter(a=>a.risk==="High").length;
  return <section className="page active portal-dashboard">
    <div className="flex-between"><div><span className="eyebrow">{roleLabel(role)} workspace</span>
      <h1 className="page-title">{role==="broker"?"Broker Dashboard":"Underwriter Dashboard"}</h1>
      <p className="subtitle">{role==="broker"?"Track your referred client loan applications.":"Review portfolio risk, decisions and underwriting activity."}</p>
    </div><span className="status demo">Fictional demo applications</span></div>
    <BackendStatus/>
    <div className="grid3 portal-metrics">
      <SummaryCard title={role==="broker"?"Assigned Applications":"Portfolio Applications"} icon="▤" value={visible.length} note="Example applications" />
      <SummaryCard title="Awaiting Decision" icon="⌁" value={visible.filter(a=>a.status==="Applied"||a.status==="AI Deliberated").length} note="Demo statuses" />
      <SummaryCard title={role==="broker"?"Average LTV":"High Risk Files"} icon="▦" value={role==="broker"?(visible.length?Math.round(visible.reduce((s,a)=>s+a.ltv,0)/visible.length)+"%":"—"):highRisk} note="Illustrative metrics only" compact />
    </div>
    <div className="panel section">
      <div className="table-head"><div><h2>{role==="broker"?"My Client Applications":"Portfolio Overview"}</h2><p style={{fontSize:12,margin:0}}>Demonstration records; no real borrower information</p></div>
        <button type="button" className="btn ghost" onClick={onOpenPipeline}>Open pipeline →</button></div>
      <div className="table-wrap"><table><thead><tr><th>File</th><th>Borrower</th><th>Requested</th><th>LTV</th><th>Risk</th><th>Stage</th></tr></thead>
      <tbody>{visible.map((a:LoanApplication)=><tr key={a.id}><td className="strong">{a.id}</td><td>{a.borrower}<div className="card-note">{a.county}</div></td><td>{euro(a.requestedAmount)}</td><td>{a.ltv.toFixed(1)}%</td><td><span className={a.risk==="High"?"risk high":a.risk==="Medium"?"risk medium":"risk low"}>{a.risk}</span></td><td>{a.status}</td></tr>)}</tbody></table></div>
    </div>
    <div className="panel pad section"><h2>Next steps</h2><p>{role==="broker"?"Loan-file details, client assignment and the assessment workflow will be developed in later stages.":"AI deliberation, macro stress testing and settlement approvals will be developed in later stages."}</p>
      {role==="underwriter"&&<button className="btn secondary" type="button" onClick={onOpenAudit}>View AI Audit placeholder →</button>}
    </div>
  </section>;
}
