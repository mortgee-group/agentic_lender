import {useEffect,useState} from "react";
import Login from "./pages/Login";
import {getCurrentUser,logout as logoutRequest,type AuthUser} from "./services/auth";
import Sidebar,{type Page} from "./components/Sidebar";
import Header from "./components/Header";
import RoleDashboard from "./pages/RoleDashboard";
import LoanPipeline from "./pages/LoanPipeline";
import LoanFileDetail from "./pages/LoanFileDetail";
import AIAuditConsole from "./pages/AIAuditConsole";
import PortalPlaceholder,{type PortalFeature} from "./pages/PortalPlaceholder";
import About from "./pages/About";
import {sampleApplications,visibleApplications} from "./data/sampleApplications";
const labels:Record<Page,string>={portal:"Dashboard",pipeline:"Loan Pipeline",files:"Loan Files","file-detail":"Loan File Details",audit:"AI Audit & Deliberation",stress:"Stress Testing",settlement:"Settlement & Export",about:"About Agentic Lender"};
const features:PortalFeature[]=["stress","settlement"];
export default function App(){
 const [authStatus,setAuthStatus]=useState<"checking"|"guest"|"authenticated">("checking");
 const [user,setUser]=useState<AuthUser|null>(null);
 const [page,setPage]=useState<Page>("portal");
 const [selectedFileId,setSelectedFileId]=useState<string|null>(null);
 const [selectedAuditId,setSelectedAuditId]=useState<string|null>(null);
 useEffect(()=>{let active=true;getCurrentUser().then(u=>{if(!active)return;setUser(u);setAuthStatus(u?"authenticated":"guest");}).catch(()=>{if(active)setAuthStatus("guest");});return ()=>{active=false;};},[]);
 const handleLogin=(u:AuthUser)=>{setUser(u);setPage("portal");setSelectedFileId(null);setSelectedAuditId(null);setAuthStatus("authenticated");};
 const handleLogout=async()=>{try{await logoutRequest();}catch(e){console.error("Logout failed",e);return;}setUser(null);setAuthStatus("guest");setPage("portal");setSelectedFileId(null);setSelectedAuditId(null);};
 const navigate=(next:Page)=>{if(user?.role==="broker"&&(next==="stress"||next==="settlement"))return;setPage(next);window.scrollTo(0,0);};
 const visible=user?visibleApplications(user.role,user.username,sampleApplications):[];
 const openFile=(id:string)=>{if(!visible.some(a=>a.id===id))return;setSelectedFileId(id);navigate("file-detail");};
 const openAudit=(id?:string)=>{if(id&&!visible.some(a=>a.id===id))return;setSelectedAuditId(id??null);navigate("audit");};
 if(authStatus==="checking")return <div className="auth-shell"><div className="auth-card panel" role="status">Checking login session…</div></div>;
 if(authStatus==="guest"||!user)return <Login onLogin={handleLogin}/>;
 const selectedFile=visible.find(a=>a.id===selectedFileId)??null;
 return <div className="layout"><Sidebar page={page} role={user.role} onNavigate={navigate}/><main className="main"><Header title={labels[page]} username={user.username} role={user.role} onLogout={handleLogout}/><div className="content">
 {page==="portal"&&<RoleDashboard role={user.role} username={user.username} onOpenPipeline={()=>navigate("pipeline")} onOpenFile={openFile} onOpenAudit={()=>openAudit()}/>}
 {(page==="pipeline"||page==="files")&&<LoanPipeline key={page+user.username} applications={visible} role={user.role} mode={page} onOpenFile={openFile}/>}
 {page==="file-detail"&&<LoanFileDetail application={selectedFile} role={user.role} onBack={()=>navigate("pipeline")} onOpenAudit={()=>selectedFile&&openAudit(selectedFile.id)}/>}
 {page==="audit"&&<AIAuditConsole applications={visible} role={user.role} selectedId={selectedAuditId} onSelect={setSelectedAuditId} onViewFile={openFile}/>}
 {features.includes(page as PortalFeature)&&<PortalPlaceholder feature={page as PortalFeature} role={user.role} onBack={()=>navigate("portal")}/>}
 {page==="about"&&<About/>}
 <footer className="foot"><span>AL-Web | Agentic Lender</span><span>Fictional data · Development environment</span></footer>
 </div></main></div>;
}
