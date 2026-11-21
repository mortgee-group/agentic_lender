import {useEffect,useState} from "react";
import {getCoreApplications,getCoreApplication,type CoreApplication} from "../services/api";
import {downloadBrowserFile,makePortfolioCsv,makeReportPdf,safeFilename} from "../utils/exportReports";

/** Underwriter-only navigation. AL-Core performs actual authorisation on every GET. */
export default function DecisionExports(){
 const [applications,setApplications]=useState<CoreApplication[]>([]);
 const [status,setStatus]=useState<"loading"|"loaded"|"error">("loading");
 const [error,setError]=useState("");
 const [actionError,setActionError]=useState("");
 const [selected,setSelected]=useState("");
 const [busy,setBusy]=useState(false);
 const [refresh,setRefresh]=useState(0);
 useEffect(()=>{
  let active=true;setStatus("loading");setError("");setApplications([]);
  getCoreApplications().then(items=>{
   if(!active)return;
   setApplications(items);setStatus("loaded");
   setSelected(current=>items.some(a=>a.applicationId===current)?current:(items[0]?.applicationId??""));
  }).catch(e=>{
   if(!active)return;
   setStatus("error");setError(e instanceof Error?e.message:"Unable to load applications.");
  });
  return()=>{active=false;};
 },[refresh]);
 const exportCsv=()=>{
  if(status!=="loaded"||applications.length===0)return;
  setActionError("");
  try{
   downloadBrowserFile(makePortfolioCsv(applications),"text/csv;charset=utf-8","agentic-lender-portfolio.csv");
  }catch(e){setActionError(e instanceof Error?e.message:"Could not create CSV.");}
 };
 const exportPdf=async()=>{
  if(status!=="loaded"||!selected||busy||!applications.some(a=>a.applicationId===selected))return;
  setBusy(true);setActionError("");
  try{
   // Fetch the selected record again through the authorised single-record API
   // to prevent exporting stale financial/decision details from the list snapshot.
   const item=await getCoreApplication(selected);
   if(item.applicationId!==selected)throw new Error("AL-Core returned a different application.");
   const bytes=makeReportPdf(item);
   downloadBrowserFile(bytes as Uint8Array<ArrayBuffer>,"application/pdf",`agentic-lender-${safeFilename(selected)}.pdf`);
  }catch(e){setActionError(e instanceof Error?e.message:"Unable to generate PDF.");}
  finally{setBusy(false);}
 };
 return <section className="page active decision-export">
  <span className="eyebrow">Underwriter workspace · Authenticated AL-Core data</span>
  <h1 className="page-title">Decision Reports & Exports</h1>
  <p className="subtitle">Export a read-only portfolio CSV or a single application's PDF report.</p>
  <div className="notice"><strong>Export is not settlement.</strong> Reports contain only fields returned by authorised AL-Core endpoints.
   Full credit memoranda, AI agent trails and blockchain proof are not exposed by the current read API and will be marked unavailable.
   This page cannot approve, sign, fund or mint a loan. Test with fictional data only.</div>
  <section className="panel pad">
   <div className="flex-between"><div><h2>Authorised application data</h2>
    <p className="core-note">Fetched from AL-Core for the signed-in Underwriter. No bundled sample applications are used.</p></div>
    <button type="button" className="btn secondary" onClick={()=>setRefresh(v=>v+1)} disabled={status==="loading"||busy}>Refresh records</button>
   </div>
   {status==="loading"&&<p role="status">Loading applications…</p>}
   {status==="error"&&<p role="alert" className="auth-error">{error}</p>}
   {status==="loaded"&&<p className="core-note" role="status">{applications.length} authorised application(s) available for export.</p>}
  </section>
  <div className="detail-columns section">
   <section className="panel pad"><h2>Portfolio CSV</h2>
    <p>Download a spreadsheet-compatible portfolio snapshot with application status, amount, LTV, risk and any recorded human decision.</p>
    <p className="core-note">A CSV does not contain an AI reasoning audit trail. Treat exported data as sensitive and store it securely.</p>
    <button className="btn primary" type="button" disabled={status!=="loaded"||applications.length===0||busy} onClick={exportCsv}>Download portfolio CSV</button>
   </section>
   <section className="panel pad"><h2>Mortgage decision PDF</h2>
    <p>Select a mortgage to generate a financial and decision summary. The record is re-fetched before export.</p>
    <div className="field"><label htmlFor="export-application">Mortgage application</label>
     <select id="export-application" disabled={status!=="loaded"||busy||applications.length===0}
      value={selected} onChange={e=>setSelected(e.target.value)}>
      {applications.length===0&&<option value="">No applications available</option>}
      {applications.map(a=><option value={a.applicationId} key={a.applicationId}>{a.applicationId} — {a.property?.county??"Unknown county"} — {a.lifecycleStatus}</option>)}
     </select></div>
    <p className="core-note">Reports state whether an approval or rejection was actually recorded; CLOSED alone does not imply rejection or approval.</p>
    <button className="btn primary" type="button" disabled={status!=="loaded"||!selected||busy}
     onClick={()=>void exportPdf()}>{busy?"Preparing PDF…":"Download mortgage PDF"}</button>
   </section>
  </div>
  {actionError&&<p className="auth-error section" role="alert">{actionError}</p>}
  <div className="panel pad section"><h2>On-chain settlement</h2>
   <p>Settlement, digital signatures, smart-contract minting and verified blockchain audit packs require a separate AL-Core/blockchain integration. This export feature performs none of these actions.</p>
   <button className="btn secondary" type="button" disabled>On-chain settlement not connected</button>
  </div>
 </section>;
}
