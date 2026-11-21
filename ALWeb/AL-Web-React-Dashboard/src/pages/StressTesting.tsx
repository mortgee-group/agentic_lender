import {useEffect,useMemo,useState} from "react";
import {getCoreApplications,type CoreApplication} from "../services/api";
import {calculateStress,type StressScenario} from "../utils/stressTesting";
import PortfolioStressTesting from "./PortfolioStressTesting";

const demo:StressScenario={
 loanAmount:280000,propertyValue:350000,annualIncome:85000,existingAnnualDebt:4800,
 mortgageTermYears:30,currentInterestPct:3.75,rateShockBps:200,propertyDropPct:10,
};
const eur=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const fmtEuro=(v:number)=>eur.format(v);
const fmtPct=(v:number)=>v.toFixed(1)+"%";
const fields=[
 {key:"loanAmount",name:"Outstanding loan / principal (€)",min:1,max:100000000,step:1000},
 {key:"propertyValue",name:"Current property value (€)",min:1,max:100000000,step:1000},
 {key:"annualIncome",name:"Gross household income (€/year)",min:1,max:100000000,step:1000},
 {key:"existingAnnualDebt",name:"Existing debt payments (€/year)",min:0,max:100000000,step:100},
 {key:"mortgageTermYears",name:"Mortgage term (years)",min:1,max:40,step:1},
 {key:"currentInterestPct",name:"Current annual interest rate (%)",min:0,max:30,step:0.25},
] as const;

const loanFromApplication=(app:CoreApplication,previous:StressScenario):StressScenario=>({
 loanAmount:typeof app.requestedLoanAmount==="number"?app.requestedLoanAmount:app.sellerAskingPrice-app.deposit,
 propertyValue:app.sellerAskingPrice,
 annualIncome:app.householdAnnualGrossIncome,
 existingAnnualDebt:app.existingDebtLiabilities,
 mortgageTermYears:app.requestedMortgageTerm,
 currentInterestPct:previous.currentInterestPct,
 rateShockBps:previous.rateShockBps,
 propertyDropPct:previous.propertyDropPct,
});
export default function StressTesting(){
 const [mode,setMode]=useState<"single"|"portfolio">("single");
 const [records,setRecords]=useState<CoreApplication[]>([]);
 const [recordsStatus,setRecordsStatus]=useState<"loading"|"loaded"|"error">("loading");
 const [recordsError,setRecordsError]=useState("");
 const [refresh,setRefresh]=useState(0);
 const [source,setSource]=useState("example");
 const [shownSource,setShownSource]=useState("example");
 const [form,setForm]=useState<StressScenario>(demo);
 const [shown,setShown]=useState<StressScenario>(demo);
 const [error,setError]=useState("");
 useEffect(()=>{
  let active=true;
  setRecordsStatus("loading");setRecordsError("");
  getCoreApplications().then(data=>{
   if(active){setRecords(data);setRecordsStatus("loaded");}
  }).catch((cause:unknown)=>{
   if(active){setRecords([]);setRecordsStatus("error");setRecordsError(cause instanceof Error?cause.message:"Unable to load applications.");}
  });
  return()=>{active=false;};
 },[refresh]);
 const choose=(id:string)=>{
  setError("");
  if(id==="example"){setSource("example");setForm(demo);return;}
  const record=records.find(a=>a.applicationId===id);
  if(!record)return;
  setSource(id);
  setForm(prev=>loanFromApplication(record,prev));
 };
 const selected=records.find(a=>a.applicationId===source);
 const result=useMemo(()=>calculateStress(shown),[shown]);
 const change=(key:keyof StressScenario,value:number)=>setForm(prev=>({...prev,[key]:value}));
 const submit=()=>{
  try{calculateStress(form);setShown({...form});setShownSource(source);setError("");}
  catch(e){setError(e instanceof Error?e.message:"Invalid inputs.");}
 };
 const changed=source!==shownSource||Object.keys(form).some(k=>form[k as keyof StressScenario]!==shown[k as keyof StressScenario]);
 const inc=result.stressedMonthlyPayment-result.originalMonthlyPayment;
 return <section className="page active stress-page">
  <div className="flex-between"><div><span className="eyebrow">Underwriter workspace · Scenario analysis</span>
   <h1 className="page-title">Macro Stress Testing</h1>
   <p className="subtitle">Explore mortgage sensitivity to interest-rate shocks and property-market downturns.</p>
  </div><span className="status demo">Local simulation · No AI inference</span></div>
  <div className="notice"><strong>Scenario analysis only.</strong> The simulator can populate financial inputs from a live AL-Core application,
   but its calculations run locally. It does not call AL-AI, use its ML valuation, change Firestore records or make a lending decision.
   The seller's asking price is a <strong>proxy</strong> for property value, and the initial interest rate is an <strong>editable assumption</strong>.
   Existing debt is treated as annual debt service, following the AL-AI calculation.</div>
  <div className="stress-mode" role="group" aria-label="Stress testing mode">
   <button type="button" className="btn secondary" aria-pressed={mode==="single"} onClick={()=>setMode("single")}>Single mortgage</button>
   <button type="button" className="btn secondary" aria-pressed={mode==="portfolio"} onClick={()=>setMode("portfolio")}>Entire active portfolio</button>
  </div>
  {mode==="portfolio"?<><div className="stress-portfolio-refresh"><button type="button" className="btn secondary" disabled={recordsStatus==="loading"} onClick={()=>setRefresh(n=>n+1)}>Refresh applications</button></div>
   {recordsStatus==="error"&&<p className="auth-error" role="alert">{recordsError}</p>}
   <PortfolioStressTesting key={refresh} applications={records} status={recordsStatus}/></>:<>
  <section className="panel pad stress-source" aria-label="Select an application">
   <div className="flex-between"><div><h2>Mortgage application</h2><p className="core-note">Select an authorised AL-Core application or continue with the fictional example.</p></div>
    <button className="btn secondary" type="button" disabled={recordsStatus==="loading"} onClick={()=>setRefresh(n=>n+1)}>Refresh applications</button></div>
   <div className="field"><label htmlFor="stress-application">Source for mortgage inputs</label>
    <select id="stress-application" value={source} onChange={e=>choose(e.target.value)} disabled={recordsStatus==="loading"}>
     <option value="example">Fictional example — €280,000 mortgage</option>
     {records.map(a=><option key={a.applicationId} value={a.applicationId}>
      {a.applicationId} — {a.property?.county??"County unknown"} — {a.lifecycleStatus}
     </option>)}
     {source!=="example"&&!records.some(a=>a.applicationId===source)&&<option value={source}>{source} — previously loaded</option>}
    </select>
   </div>
   {recordsStatus==="loading"&&<p className="core-note" role="status">Loading accessible applications from AL-Core…</p>}
   {recordsStatus==="error"&&<p className="auth-error" role="alert">Could not load applications: {recordsError}. The fictional example remains available.</p>}
   {recordsStatus==="loaded"&&records.length===0&&<p className="core-note">No mortgage applications are available to this Underwriter. You can still use the fictional example.</p>}
   {selected&&<div className="stress-source-meta" role="status">
    <strong>Live application inputs loaded</strong>
    <span>Application: {selected.applicationId}</span>
    <span>County: {selected.property?.county??"—"}</span>
    <span>Lifecycle: {selected.lifecycleStatus}</span>
    <span>Property value field uses the seller asking price ({fmtEuro(selected.sellerAskingPrice)}), not a verified valuation.</span>
    <span>Current rate defaults to {form.currentInterestPct.toFixed(2)}%; AL-Core doesn't provide a contractual interest rate. Verify before running.</span>
   </div>}
   {source!==shownSource&&<p className="core-note">New application selected. Run the stress test to replace the currently displayed results.</p>}
  </section>
  <div className="stress-layout">
   <section className="panel pad stress-controls"><h2>Mortgage and property inputs</h2>
    {source!=="example"&&<p className="core-note">Fields were populated from the selected application. Check and adjust the value, existing annual debt payments and assumed rate before calculating.</p>}
    <div className="stress-fields">{fields.map(f=><div className="field" key={f.key}>
     <label htmlFor={`stress-${f.key}`}>{f.name}</label>
     <input id={`stress-${f.key}`} type="number" min={f.min} max={f.max} step={f.step} required
      value={form[f.key]} onChange={e=>change(f.key,e.target.valueAsNumber)} />
    </div>)}</div>
    <h2 className="stress-section-title">Stress scenario</h2>
    <div className="field"><label htmlFor="stress-rate">Interest-rate shock: +{form.rateShockBps} basis points
     (+{(form.rateShockBps/100).toFixed(2)} percentage points)</label>
     <input id="stress-rate" type="range" min="0" max="500" step="25" value={form.rateShockBps}
      onChange={e=>change("rateShockBps",Number(e.target.value))}/></div>
    <div className="stress-shortcuts" aria-label="Rate scenarios">{[0,100,200,300].map(n=>
     <button key={n} type="button" className="btn secondary" aria-pressed={form.rateShockBps===n}
      onClick={()=>change("rateShockBps",n)}>+{n} bps</button>)}</div>
    <div className="field"><label htmlFor="stress-drop">Property value downturn: {form.propertyDropPct}%</label>
     <input id="stress-drop" type="range" min="0" max="50" step="5" value={form.propertyDropPct}
      onChange={e=>change("propertyDropPct",Number(e.target.value))}/></div>
    <div className="stress-shortcuts" aria-label="Property downturn scenarios">{[0,10,20,30].map(n=>
     <button key={n} type="button" className="btn secondary" aria-pressed={form.propertyDropPct===n}
      onClick={()=>change("propertyDropPct",n)}>{n}% drop</button>)}</div>
    {error&&<p className="auth-error" role="alert">{error}</p>}
    <div className="stress-buttons">
     <button type="button" className="btn primary" onClick={submit}>Run local stress test</button>
     <button type="button" className="btn secondary" onClick={()=>{setForm(demo);setShown(demo);setSource("example");setShownSource("example");setError("");}}>Reset example</button>
    </div>
    {changed&&<p className="core-note" role="status">Inputs changed — run the stress test to update the results.</p>}
   </section>
   <section className="stress-output" aria-label="Stress test results">
    <div className="panel pad">
     <div className="flex-between"><h2>Scenario results</h2><span className="status demo">Illustrative calculation</span></div>
     <p className="core-note">Results based on {shownSource==="example"?"fictional example":`application ${shownSource}`} at +{shown.rateShockBps} bps interest and {shown.propertyDropPct}% property decline.{changed?" Some inputs have changed since this run.":""}</p>
     <div className="stress-metric-grid">
      <div><span>Current repayment / month</span><strong>{fmtEuro(result.originalMonthlyPayment)}</strong></div>
      <div><span>Stressed repayment / month</span><strong>{fmtEuro(result.stressedMonthlyPayment)}</strong></div>
      <div><span>Monthly repayment increase</span><strong>{fmtEuro(inc)}</strong></div>
      <div><span>Stressed property value</span><strong>{fmtEuro(result.stressedPropertyValue)}</strong></div>
     </div>
     <div className="stress-verdict">
      <span className="eyebrow">Scenario classification</span>
      <strong>{result.verdict}</strong>
      <p>Rule-based scenario classification only. Not a credit decision or regulatory determination.</p>
     </div>
    </div>
    <div className="panel pad section"><h2>Before and after</h2>
     <div className="table-wrap"><table className="stress-table">
      <thead><tr><th>Measure</th><th>Current</th><th>Stressed</th></tr></thead>
      <tbody>
       <tr><td>Annual interest rate</td><td>{fmtPct(shown.currentInterestPct)}</td><td>{fmtPct(shown.currentInterestPct+shown.rateShockBps/100)}</td></tr>
       <tr><td>Debt-to-income</td><td>{fmtPct(result.originalDTI)}</td><td>{fmtPct(result.stressedDTI)}</td></tr>
       <tr><td>Loan-to-value</td><td>{fmtPct(result.originalLTV)}</td><td>{fmtPct(result.stressedLTV)}</td></tr>
       <tr><td>Rule-based risk rating (1–5)</td><td>{result.originalRiskRating}</td><td>{result.stressedRiskRating}</td></tr>
      </tbody>
     </table></div>
     <p className="core-note">DTI = (annualised mortgage repayments + existing annual debt payments) / gross annual income. LTV uses the entered property value, not an ML estimate. Calculations assume a fully amortising repayment mortgage at a fixed scenario rate over the entered term.</p>
    </div>
   </section>
  </div>
  </>}
 </section>;
}
