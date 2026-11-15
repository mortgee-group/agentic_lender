import type {LoanApplication} from "../data/sampleApplications";
import {euro} from "../utils/loanCalculations";
/** Borrowers see their own application progress, not internal underwriting or AI assessments. */
export default function BorrowerApplicationDetail({application,onBack}:{application:LoanApplication|null;onBack:()=>void}){
 if(!application)return <section className="page active"><h1 className="page-title">Application unavailable</h1><p className="subtitle">This application is not assigned to your demo account.</p><button className="btn secondary" type="button" onClick={onBack}>← My Applications</button></section>;
 return <section className="page active">
  <div className="flex-between"><div><span className="eyebrow">My mortgage application · {application.id}</span><h1 className="page-title">Application status</h1><p className="subtitle">Your illustrative mortgage application progress.</p></div>
   <button className="btn secondary" type="button" onClick={onBack}>← My Applications</button></div>
  <div className="notice"><strong>Fictional data.</strong> This is not a live lending application or a mortgage decision.</div>
  <div className="panel pad section"><h2>Application overview</h2>
   <div className="detail-row"><span>Application number</span><strong>{application.id}</strong></div>
   <div className="detail-row"><span>Requested loan</span><strong>{euro(application.requestedAmount)}</strong></div>
   <div className="detail-row"><span>Property location</span><strong>{application.county}</strong></div>
   <div className="detail-row"><span>Submitted</span><strong>{application.submittedDate}</strong></div>
   <div className="detail-row"><span>Current stage</span><strong>{application.status}</strong></div>
  </div>
  <div className="panel pad section"><h2>Next steps</h2><p>Application tracking is currently read-only. Updates, document upload and borrower notifications will be integrated later.</p></div>
 </section>;
}
