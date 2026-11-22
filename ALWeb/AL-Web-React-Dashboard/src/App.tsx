import {useEffect,useState} from "react";
import Login from "./pages/Login";
import {getCurrentUser,logout as logoutRequest,type AuthUser} from "./services/auth";
import Sidebar,{type Page} from "./components/Sidebar";
import Header from "./components/Header";
import BackendStatus from "./components/BackendStatus";
import LiveApplications from "./components/LiveApplications";
import LoanPipeline from "./pages/LoanPipeline";
import LiveApplicationDetail from "./pages/LiveApplicationDetail";
import NewMortgageApplication from "./pages/NewMortgageApplication";
import BorrowerApplicationDetail from "./pages/BorrowerApplicationDetail";
import LoanFileDetail from "./pages/LoanFileDetail";
import AIAuditConsole from "./pages/AIAuditConsole";
import PortalPlaceholder,{type PortalFeature} from "./pages/PortalPlaceholder";
import StressTesting from "./pages/StressTesting";
import DecisionExports from "./pages/DecisionExports";
import About from "./pages/About";
import {sampleApplications,visibleApplications} from "./data/sampleApplications";
const labels:Record<Page,string>={"system-status":"AL-Core Connection Status","live-detail":"Live Application Details","new-application":"New Mortgage Application",portal:"Dashboard",pipeline:"Loan Pipeline",files:"Loan Files","file-detail":"Loan File Details",audit:"AI Audit & Deliberation",stress:"Stress Testing",settlement:"Settlement & Export",about:"About Agentic Lender"};
const features:PortalFeature[]=[];
export default function App(){
 const [authStatus,setAuthStatus]=useState<"checking"|"guest"|"authenticated">("checking");
 const [user,setUser]=useState<AuthUser|null>(null);
 const [page,setPage]=useState<Page>("portal");
 const [selectedFileId,setSelectedFileId]=useState<string|null>(null);
 const [selectedLiveId,setSelectedLiveId]=useState<string|null>(null);
 const [liveReturnPage,setLiveReturnPage]=useState<Page>("files");
 const [selectedAuditId,setSelectedAuditId]=useState<string|null>(null);
 const [liveRefresh,setLiveRefresh]=useState(0);
 const [submissionMessage,setSubmissionMessage]=useState("");
 useEffect(()=>{let active=true;getCurrentUser().then(u=>{if(!active)return;setUser(u);setAuthStatus(u?"authenticated":"guest");}).catch(()=>{if(active)setAuthStatus("guest");});return ()=>{active=false;};},[]);
 const handleLogin=(u:AuthUser)=>{setSubmissionMessage("");setLiveRefresh(0);setUser(u);setPage("portal");setSelectedFileId(null);setSelectedLiveId(null);setSelectedAuditId(null);setAuthStatus("authenticated");};
 const handleLogout=async()=>{try{await logoutRequest();}catch(e){console.error("Logout failed",e);return;}setSubmissionMessage("");setLiveRefresh(0);setUser(null);setAuthStatus("guest");setPage("portal");setSelectedFileId(null);setSelectedAuditId(null);};
 const navigate=(next:Page)=>{
  if(user?.role==="borrower"&&!["portal","files","file-detail","live-detail","about","system-status","new-application"].includes(next))return;
  if(user?.role==="broker"&&(next==="stress"||next==="settlement"))return;
  setPage(next);window.scrollTo(0,0);
 };
 const onSubmitted=(id:string)=>{setSubmissionMessage(`Application ${id} created in Firestore.`);setLiveRefresh(v=>v+1);navigate("files");};
 const visible=user?visibleApplications(user.role,user.username,sampleApplications):[];
 const openLive=(id:string)=>{setSelectedLiveId(id);setLiveReturnPage(page==="portal"||page==="pipeline"||page==="files"?page:"files");navigate("live-detail");};
 const openFile=(id:string)=>{if(!visible.some(a=>a.id===id))return;setSelectedFileId(id);navigate("file-detail");};
 const openAudit=(id?:string)=>{if(user?.role==="borrower")return;if(id&&!visible.some(a=>a.id===id))return;setSelectedAuditId(id??null);navigate("audit");};
 if(authStatus==="checking")return <div className="auth-shell"><div className="auth-card panel" role="status">Checking login session…</div></div>;
 if(authStatus==="guest"||!user)return <Login onLogin={handleLogin}/>;
 const selectedFile=visible.find(a=>a.id===selectedFileId)??null;
 return <div className="layout"><Sidebar page={page} role={user.role} onNavigate={navigate}/><main className="main"><Header title={labels[page]} username={user.username} role={user.role} onLogout={handleLogout} isDemo={["audit","file-detail"].includes(page)}/><div className="content">
 {page==="system-status"&&<><h1 className="page-title">AL-Core connection status</h1><p className="subtitle">Firebase authentication, backend profile and verified role access.</p><BackendStatus key={user.uid} user={user}/></>}
 {submissionMessage&&(page==="portal"||page==="files")&&<div className="notice" role="status">{submissionMessage}</div>}
 {(page==="portal"||page==="files")&&<LiveApplications key={`${user.uid}-${liveRefresh}`} user={user} onOpenApplication={openLive} dashboard={page==="portal"}/>}
 {page==="pipeline"&&user.role!=="borrower"&&<LoanPipeline key={user.uid} user={user} onOpenFile={openLive}/>}
 {page==="live-detail"&&selectedLiveId&&<LiveApplicationDetail key={`${user.uid}-${selectedLiveId}`} applicationId={selectedLiveId} role={user.role} onBack={()=>navigate(liveReturnPage)}/>}
 {page==="new-application"&&user.role==="borrower"&&<NewMortgageApplication onSaved={onSubmitted} onCancel={()=>navigate("files")}/>}
 {page==="file-detail"&&(user.role==="borrower"?<BorrowerApplicationDetail application={selectedFile} onBack={()=>navigate("files")}/>:<LoanFileDetail application={selectedFile} role={user.role} onBack={()=>navigate("pipeline")} onOpenAudit={()=>selectedFile&&openAudit(selectedFile.id)}/>)}
 {page==="audit"&&user.role!=="borrower"&&<AIAuditConsole applications={visible} role={user.role} selectedId={selectedAuditId} onSelect={setSelectedAuditId} onViewFile={openFile}/>}
 {page==="stress"&&user.role==="underwriter"&&<StressTesting/>}
 {page==="settlement"&&user.role==="underwriter"&&<DecisionExports/>}
 {user.role==="underwriter"&&features.includes(page as PortalFeature)&&<PortalPlaceholder feature={page as PortalFeature} role={user.role} onBack={()=>navigate("portal")}/>}
 {page==="about"&&<About/>}
 <footer className="foot"><span>AL-Web | Agentic Lender</span><span>AL-Core live records · AI Audit examples are fictional</span></footer>
 </div></main></div>;
}
