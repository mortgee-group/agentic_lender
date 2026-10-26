import {useState} from "react";
import type {LoanApplication} from "../data/sampleApplications";
import {exampleAudit,type AgentId} from "../data/sampleAudit";
import type {PortalRole} from "../types/roles";
import {euro,percent} from "../utils/loanCalculations";
type Props={applications:LoanApplication[];role:PortalRole;selectedId:string|null;onSelect:(id:string)=>void;onViewFile:(id:string)=>void};
const names:{id:AgentId;short:string}[]=[{id:"affordability",short:"Affordability"},{id:"valuation",short:"Valuation"},{id:"market",short:"Market & Macro"},{id:"strategist",short:"Underwriting"}];
export default function AIAuditConsole({applications,role,selectedId,onSelect,onViewFile}:Props){
 const [expanded,setExpanded]=useState<AgentId|null>("affordability");
 const selected=applications.find(a=>a.id===selectedId)??applications[0]??null;
 const audit=selected?exampleAudit(selected):null;
 return <section className="page active">
  <div className="flex-between"><div><span className="eyebrow">{role==="broker"?"Assigned client files":"Portfolio oversight"}</span>
   <h1 className="page-title">AI Audit & Deliberation</h1>
   <p className="subtitle">Review agent responsibilities, illustrative findings and the proposed decision trail for each loan file.</p>
  </div><span className="status demo">Synthetic assessments · No AI inference</span></div>
  <div className="notice"><strong>Demonstration only.</strong> Agent commentary and recommendations are predefined examples, not actual ML results, live market research, financial advice or approvals.</div>
  <div className="panel pad audit-selector">
   <div className="field"><label htmlFor="audit-application">Application</label>
    <select id="audit-application" value={selected?.id??""} onChange={e=>{onSelect(e.target.value);setExpanded("affordability");}}>
     {applications.map(a=><option key={a.id} value={a.id}>{a.id} — {a.borrower} ({a.status})</option>)}
    </select></div>
   {selected&&<button className="btn secondary" type="button" onClick={()=>onViewFile(selected.id)}>View loan file →</button>}
  </div>
  {!selected&&<div className="panel pad section"><h2>No accessible applications</h2><p>No applications are available to this account.</p></div>}
  {selected&&<><div className="grid3 portal-metrics">
   <div className="panel stat"><div className="label">Application</div><div className="value audit-value">{selected.id}</div><div className="mini">{selected.borrower}</div></div>
   <div className="panel stat"><div className="label">Loan requested</div><div className="value audit-value">{euro(selected.requestedAmount)}</div><div className="mini">Fictional figures</div></div>
   <div className="panel stat"><div className="label">Loan-to-value</div><div className="value audit-value">{percent(selected.ltv)}</div><div className="mini">{selected.risk} sample risk</div></div>
  </div>
  {!audit?<div className="panel pad section"><h2>Awaiting AI deliberation</h2><p>This example application is still in the Applied state, so no recommendation is displayed.</p><p className="disclaimer">No assessment has been run. The AL-AI service will provide records after integration.</p></div>:<>
   <div className="panel pad section"><span className="eyebrow">Illustrative consensus</span><h2 className="audit-decision">{audit.recommendation}</h2><p>{audit.consensus}</p>
    <span className="status demo">Synthetic recommendation — not an approval</span></div>
   <div className="section"><h2>Agent assessments</h2><p className="subtitle">Select an agent to inspect its example observations and supporting values.</p>
    <div className="audit-agent-grid">
     {audit.agents.map(agent=><div className="panel audit-agent" key={agent.id}>
      <button type="button" className="audit-agent-toggle" onClick={()=>setExpanded(expanded===agent.id?null:agent.id)} aria-expanded={expanded===agent.id}>
       <span><strong>{agent.name}</strong><small>{names.find(n=>n.id===agent.id)?.short} · {agent.status}</small></span>
       <span aria-hidden="true">{expanded===agent.id?"−":"+"}</span>
      </button>
      <p>{agent.finding}</p>
      {expanded===agent.id&&<ul className="audit-evidence">{agent.evidence.map((item,i)=><li key={i}>{item}</li>)}</ul>}
     </div>)}
    </div>
   </div>
   <div className="panel pad section"><h2>Deliberation trail</h2><ol className="audit-trail">{audit.events.map((event,i)=><li key={i}><strong>{event.step}</strong><p>{event.detail}</p></li>)}</ol></div>
  </>}
  </>}
 </section>;
}
