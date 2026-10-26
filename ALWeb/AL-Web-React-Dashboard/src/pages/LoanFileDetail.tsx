import type {LoanApplication} from "../data/sampleApplications";
import type {PortalRole} from "../types/roles";
import {demoDebtRatio,euro,percent,valuationGap} from "../utils/loanCalculations";
export default function LoanFileDetail({application,role,onBack}:{application:LoanApplication|null;role:PortalRole;onBack:()=>void}){
 if(!application)return <section className="page active"><h1 className="page-title">Loan file unavailable</h1><p className="subtitle">This file could not be found in your permitted demonstration applications.</p><button className="btn secondary" onClick={onBack}>← Return to pipeline</button></section>;
 const gap=valuationGap(application);
 return <section className="page active">
  <div className="flex-between"><div><span className="eyebrow">{role==="broker"?"Assigned borrower file":"Underwriter collateral review"} · {application.id}</span>
   <h1 className="page-title">{application.borrower}</h1><p className="subtitle">{application.propertyAddress}, {application.county} · {application.propertyType}</p></div>
   <button type="button" className="btn secondary" onClick={onBack}>← Back to pipeline</button></div>
  <div className="notice"><strong>Illustrative information only.</strong> This is not an actual mortgage application, verified valuation, affordability assessment or underwriting decision.</div>
  <div className="grid3 portal-metrics">
   <div className="panel stat"><div className="label">Requested loan</div><div className="value">{euro(application.requestedAmount)}</div><div className="mini">Fictional application</div></div>
   <div className="panel stat"><div className="label">Loan-to-value</div><div className="value">{percent(application.ltv)}</div><div className="mini">Requested loan / sample ML value</div></div>
   <div className="panel stat"><div className="label">Risk tier</div><div className="value"><span className={`risk ${application.risk.toLowerCase()}`}>{application.risk}</span></div><div className="mini">{application.status}</div></div>
  </div>
  <div className="detail-columns section">
   <div className="panel pad"><h2>Collateral assessment</h2>
    <div className="detail-row"><span>Seller asking price</span><strong>{euro(application.askingPrice)}</strong></div>
    <div className="detail-row"><span>Illustrative ML valuation</span><strong>{euro(application.propertyValue)}</strong></div>
    <div className="detail-row"><span>Asking price above/below valuation</span><strong>{gap===0?"No difference":`${euro(Math.abs(gap))} ${gap>0?"above":"below"}`}</strong></div>
    <div className="detail-row"><span>Mortgage requested</span><strong>{euro(application.requestedAmount)}</strong></div>
    <div className="detail-row"><span>Loan-to-value</span><strong>{percent(application.ltv)}</strong></div>
   </div>
   <div className="panel pad"><h2>Borrower affordability snapshot</h2>
    <div className="detail-row"><span>Annual gross income</span><strong>{euro(application.annualIncome)}</strong></div>
    <div className="detail-row"><span>Existing annual debt payments</span><strong>{euro(application.annualDebtPayments)}</strong></div>
    <div className="detail-row"><span>Debt payment / gross income</span><strong>{percent(demoDebtRatio(application))}</strong></div>
    <p className="disclaimer">This illustrative payment-to-income ratio is not the final DTI calculation; full income, debt, mortgage terms and policy rules require backend integration.</p>
   </div>
  </div>
  <div className="panel pad section"><h2>Application status</h2>
   <div className="detail-row"><span>Lifecycle status</span><strong>{application.status}</strong></div>
   <div className="detail-row"><span>Submitted</span><strong>{application.submittedDate}</strong></div>
   <div className="detail-row"><span>Assigned broker</span><strong>{application.broker}</strong></div>
   <p className="disclaimer">The AI reasoning record, approval signatures and immutable ledger trail will be connected in later stages. This page is read-only.</p>
  </div>
 </section>;
}
