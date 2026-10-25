import type {PortalRole} from "../types/roles";
export type Page = "portal" | "pipeline" | "files" | "audit" | "stress" | "settlement" | "dashboard" | "new" | "insights" | "history" | "about" | "results";
type Link={page:Page;icon:string;label:string};
const brokerLinks:Link[]=[
 {page:"portal",icon:"▦",label:"Broker Dashboard"},
 {page:"pipeline",icon:"▤",label:"Assigned Pipeline"},
 {page:"files",icon:"⌑",label:"Loan Files"},
 {page:"audit",icon:"✧",label:"AI Audit"}
];
const underwriterLinks:Link[]=[
 {page:"portal",icon:"▦",label:"Underwriter Dashboard"},
 {page:"pipeline",icon:"▤",label:"Loan Pipeline"},
 {page:"files",icon:"⌑",label:"Loan Assessments"},
 {page:"audit",icon:"✧",label:"AI Audit"},
 {page:"stress",icon:"⌁",label:"Stress Testing"},
 {page:"settlement",icon:"✓",label:"Settlement & Export"}
];
const demoLinks:Link[]=[
 {page:"dashboard",icon:"▦",label:"Valuation Demo"},
 {page:"new",icon:"⊕",label:"New Valuation"},
 {page:"insights",icon:"✧",label:"Example Insights"},
 {page:"history",icon:"▤",label:"Demo History"},
 {page:"about",icon:"ⓘ",label:"About Project"}
];
export default function Sidebar({page,role,onNavigate}:{page:Page;role:PortalRole;onNavigate:(page:Page)=>void}){
 const render=(links:Link[])=>links.map(link=><button type="button" key={link.page} className={page===link.page?"active":""} onClick={()=>onNavigate(link.page)} aria-current={page===link.page?"page":undefined}>
   <span className="ico" aria-hidden="true">{link.icon}</span><span className="navtext">{link.label}</span></button>);
 return <aside className="sidebar">
   <div className="logo"><span className="mark">⌂</span><span className="logotext">AL-Web</span></div>
   <div className="small-label">WORKSPACE</div><nav className="nav" aria-label="Main navigation">{render(role==="broker"?brokerLinks:underwriterLinks)}</nav>
   <div className="small-label">LEGACY DEMO TOOLS</div><nav className="nav" aria-label="Prototype screens">{render(demoLinks)}</nav>
   <div className="sidebar-foot">Agentic Lender · Stage 1<br/>Role portals · Fictional data</div>
 </aside>;
}
