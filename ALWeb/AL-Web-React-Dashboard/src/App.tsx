import {useEffect,useState} from "react";
import Login from "./pages/Login";
import {getCurrentUser,logout as logoutRequest,type AuthUser} from "./services/auth";
import Sidebar,{type Page} from "./components/Sidebar";
import Header from "./components/Header";
import RoleDashboard from "./pages/RoleDashboard";
import PortalPlaceholder,{type PortalFeature} from "./pages/PortalPlaceholder";
import Dashboard,{type QuickProperty} from "./pages/Dashboard";
import NewValuation from "./pages/NewValuation";
import ValuationResults from "./pages/ValuationResults";
import AIInsights from "./pages/AIInsights";
import ValuationHistory from "./pages/ValuationHistory";
import About from "./pages/About";
import type {ValuationRequest} from "./types/valuationRequest";
import {sampleValuations} from "./data/sampleValuations";
import type {PropertyRecord} from "./types/property";
import {makeDemoRecord} from "./utils/valuation";
const labels:Record<Page,string>={portal:"Dashboard",pipeline:"Loan Pipeline",files:"Loan Files",audit:"AI Audit",stress:"Stress Testing",settlement:"Settlement & Export",dashboard:"Valuation Demo",new:"New Valuation",results:"Valuation Results",insights:"Example Insights",history:"Demo History",about:"About Project"};
const features:PortalFeature[]=["pipeline","files","audit","stress","settlement"];
export default function App(){
 const [authStatus,setAuthStatus]=useState<"checking"|"guest"|"authenticated">("checking");
 const [user,setUser]=useState<AuthUser|null>(null);
 const [page,setPage]=useState<Page>("portal");
 const [records,setRecords]=useState<PropertyRecord[]>(()=>[...sampleValuations]);
 const [selected,setSelected]=useState<PropertyRecord|null>(sampleValuations[0]);
 const [quick,setQuick]=useState<QuickProperty|null>(null);
 const [editing,setEditing]=useState<ValuationRequest|null>(null);
 const [formVersion,setFormVersion]=useState(0);
 useEffect(()=>{let active=true;getCurrentUser().then(u=>{if(!active)return;setUser(u);setAuthStatus(u?"authenticated":"guest");}).catch(()=>{if(active)setAuthStatus("guest");});return ()=>{active=false;};},[]);
 const handleLogin=(u:AuthUser)=>{setUser(u);setPage("portal");setAuthStatus("authenticated");};
 const handleLogout=async()=>{try{await logoutRequest();}catch(e){console.error("Logout failed",e);return;}setUser(null);setAuthStatus("guest");setPage("portal");setRecords([...sampleValuations]);setSelected(sampleValuations[0]);setEditing(null);setQuick(null);};
 const navigate=(next:Page)=>{if(user?.role==="broker"&&(next==="stress"||next==="settlement"))return;setPage(next);window.scrollTo(0,0);};
 const openNew=(details?:QuickProperty)=>{setQuick(details??null);setEditing(null);setFormVersion(n=>n+1);navigate("new");};
 const submit=(request:ValuationRequest)=>{const record=makeDemoRecord(request,Date.now());setRecords(old=>[record,...old]);setSelected(record);setEditing(request);navigate("results");};
 const openRecord=(record:PropertyRecord)=>{setSelected(record);setEditing(null);navigate("results");};
 const edit=()=>{if(!editing)return;setQuick(null);setFormVersion(n=>n+1);navigate("new");};
 if(authStatus==="checking")return <div className="auth-shell"><div className="auth-card panel" role="status">Checking login session…</div></div>;
 if(authStatus==="guest"||!user)return <Login onLogin={handleLogin}/>;
 return <div className="layout"><Sidebar page={page} role={user.role} onNavigate={navigate}/><main className="main"><Header title={labels[page]} username={user.username} role={user.role} onLogout={handleLogout}/><div className="content">
 {page==="portal"&&<RoleDashboard role={user.role} username={user.username} onOpenPipeline={()=>navigate("pipeline")} onOpenAudit={()=>navigate("audit")}/>}
 {features.includes(page as PortalFeature)&&<PortalPlaceholder feature={page as PortalFeature} role={user.role} onBack={()=>navigate("portal")}/>}
 {page==="dashboard"&&<Dashboard records={records} onNewValuation={openNew} onInsights={()=>navigate("insights")} onHistory={()=>navigate("history")} onViewRecord={openRecord}/>}
 {page==="new"&&<NewValuation key={formVersion} initialValues={editing??quick} onSubmit={submit}/>}
 {page==="results"&&<ValuationResults record={selected} onNew={()=>openNew()} onInsights={()=>navigate("insights")} onHistory={()=>navigate("history")} onEdit={editing?edit:undefined}/>}
 {page==="insights"&&<AIInsights record={selected} onNew={()=>openNew()} onResults={()=>navigate("results")}/>}
 {page==="history"&&<ValuationHistory records={records} onView={openRecord} onNew={()=>openNew()}/>}
 {page==="about"&&<About/>}
 <footer className="foot"><span>AL-Web | Stage 1 Role Portal</span><span>Prototype · Fictional data only</span></footer>
 </div></main></div>;
}
