import type {PortalRole} from "../types/roles";
const details={
 audit:{title:"AI Audit & Deliberation Console",description:"Agent reasoning logs, consensus and individual decision trails."},
 stress:{title:"Macro Stress Testing",description:"Simulate interest-rate shocks and property-market downturns."},
 settlement:{title:"Settlement & Exports",description:"Authorised approvals, on-chain settlement and PDF/CSV audit exports."}
} as const;
export type PortalFeature=keyof typeof details;
export default function PortalPlaceholder({feature,role,onBack}:{feature:PortalFeature;role:PortalRole;onBack:()=>void}){
 const item=details[feature];
 return <section className="page active placeholder-content"><span className="eyebrow">{role==="broker"?"Broker":"Underwriter"} portal · upcoming module</span>
  <h1 className="page-title">{item.title}</h1><p className="subtitle">{item.description}</p>
  <div className="panel pad"><h2>Future implementation stage</h2><p>This screen is reserved for a later stage. It does not call AL-Core or modify any loan or blockchain record.</p>
    <p>Development will use agreed API contracts and mocked responses until the corresponding backend service is available.</p>
    <button type="button" className="btn secondary" onClick={onBack}>← Back to dashboard</button>
  </div></section>;
}
