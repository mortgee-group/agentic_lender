import type {LoanApplication} from "../data/sampleApplications";
import {euro} from "../utils/loanCalculations";
type Props={username:string;applications:LoanApplication[];onOpenApplications:()=>void;onOpenFile:(id:string)=>void;listOnly?:boolean};
/** Read-only borrower demonstration. Backend must enforce record ownership once integrated. */
export default function BorrowerDashboard({username,applications,onOpenApplications,onOpenFile,listOnly=false}:Props){
 const latest=[...applications].sort((a,b)=>b.submittedDate.localeCompare(a.submittedDate))[0];
 return <section className="page active portal-dashboard">
  <div className="flex-between"><div><span className="eyebrow">Borrower workspace</span>
   <h1 className="page-title">{listOnly?"My Applications":"Borrower Dashboard"}</h1>
   <p className="subtitle">{listOnly?"View your fictional mortgage applications and their progress.":`Welcome, ${username}. Track your sample mortgage application status.`}</p>
  </div><span className="status demo">Fictional applications</span></div>
  <div className="notice"><strong>Demonstration only.</strong> No real personal or financial information is stored in this application.</div>
  {!listOnly&&<div className="grid3 portal-metrics">
   <div className="panel stat"><div className="label">My applications</div><div className="value">{applications.length}</div><div className="mini">Assigned sample records</div></div>
   <div className="panel stat"><div className="label">Latest status</div><div className="value audit-value">{latest?.status??"None"}</div><div className="mini">{latest?.id??"No application"}</div></div>
   <div className="panel stat"><div className="label">Latest amount</div><div className="value audit-value">{latest?euro(latest.requestedAmount):"—"}</div><div className="mini">Illustrative amount only</div></div>
  </div>}
  <div className="panel section">
   <div className="table-head"><div><h2>My Mortgage Applications</h2><p style={{margin:0,fontSize:12}}>Read-only sample records assigned to this demo account</p></div>
    {!listOnly&&<button className="btn ghost" type="button" onClick={onOpenApplications}>View all applications →</button>}</div>
   {applications.length===0?<div className="empty">No sample applications have been assigned to this borrower account.</div>:
   <div className="table-wrap"><table><thead><tr><th>Application</th><th>Property location</th><th>Requested</th><th>Submitted</th><th>Status</th><th></th></tr></thead>
    <tbody>{applications.map(a=><tr key={a.id}><td className="strong">{a.id}</td><td>{a.county}</td><td>{euro(a.requestedAmount)}</td><td>{a.submittedDate}</td><td>{a.status}</td>
    <td><button className="link" type="button" onClick={()=>onOpenFile(a.id)}>View status →</button></td></tr>)}</tbody></table></div>}
  </div>
  <div className="panel pad section"><h2>Submit a new application</h2><p>Application submission and document upload will be connected in a future stage. This demonstration is read-only.</p></div>
 </section>;
}
