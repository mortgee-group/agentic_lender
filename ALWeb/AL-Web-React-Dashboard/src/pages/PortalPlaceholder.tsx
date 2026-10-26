import type {PortalRole} from "../types/roles";
const details={
 stress:{title:"Macro Stress Testing",description:"Simulate interest-rate shocks and property-market downturns."},
 settlement:{title:"Settlement & Exports",description:"Authorised approvals, on-chain settlement and PDF/CSV audit exports."}
} as const;
export type PortalFeature=keyof typeof details;
export default function PortalPlaceholder({feature,role,onBack}:{feature:PortalFeature;role:PortalRole;onBack:()=>void}){
 const item=details[feature];
 return <section className="page active placeholder-content"><span className="eyebrow">{role==="broker"?"Broker":"Underwriter"} workspace · planned feature</span>
  <h1 className="page-title">{item.title}</h1><p className="subtitle">{item.description}</p>
  <div className="panel pad"><h2>Backend integration pending</h2><p>This feature will be completed in a later stage. It does not modify loan records or blockchain state.</p>
   <button type="button" className="btn secondary" onClick={onBack}>← Back to dashboard</button></div>
 </section>;
}
