import type {PortalRole} from "../types/roles";
export type Page="portal"|"pipeline"|"files"|"file-detail"|"audit"|"stress"|"settlement"|"about";
type Link={page:Page;icon:string;label:string};
const brokerLinks:Link[]=[
 {page:"portal",icon:"▦",label:"Dashboard"},
 {page:"pipeline",icon:"▤",label:"Assigned Pipeline"},
 {page:"files",icon:"⌑",label:"Loan Files"},
 {page:"audit",icon:"✧",label:"AI Audit"}
];
const underwriterLinks:Link[]=[
 {page:"portal",icon:"▦",label:"Dashboard"},
 {page:"pipeline",icon:"▤",label:"Loan Pipeline"},
 {page:"files",icon:"⌑",label:"Loan Assessments"},
 {page:"audit",icon:"✧",label:"AI Audit"},
 {page:"stress",icon:"⌁",label:"Stress Testing"},
 {page:"settlement",icon:"✓",label:"Settlement & Export"}
];
export default function Sidebar({page,role,onNavigate}:{page:Page;role:PortalRole;onNavigate:(page:Page)=>void}){
 const links=role==="broker"?brokerLinks:underwriterLinks;
 return <aside className="sidebar">
   <div className="logo"><span className="mark">⌂</span><span className="logotext">Agentic Lender</span></div>
   <div className="small-label">WORKSPACE</div>
   <nav className="nav" aria-label="Main navigation">{links.map(link=><button type="button" key={link.page}
    className={page===link.page||(page==="file-detail"&&link.page==="files")?"active":""}
    onClick={()=>onNavigate(link.page)} aria-current={page===link.page?"page":undefined}>
    <span className="ico" aria-hidden="true">{link.icon}</span><span className="navtext">{link.label}</span></button>)}</nav>
   <div className="sidebar-foot"><button className="link" type="button" onClick={()=>onNavigate("about")}>About the project</button><div>AL-Web · Mortgage portal</div></div>
 </aside>;
}
