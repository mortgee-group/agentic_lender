import {useState} from "react";
import type {CoreApplication} from "../services/api";
import {calculatePortfolio,type PortfolioResult} from "../utils/portfolioStressTesting";

type Props={applications:CoreApplication[];status:"loading"|"loaded"|"error"};
const money=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const pct=(n:number)=>n.toFixed(1)+"%";

export default function PortfolioStressTesting({applications,status}:Props){
 const [rate,setRate]=useState(3.75);
 const [shock,setShock]=useState(200);
 const [drop,setDrop]=useState(10);
 const [run,setRun]=useState<{result:PortfolioResult;rate:number;shock:number;drop:number}|null>(null);
 const [error,setError]=useState("");
 const runScenario=()=>{
  if(status!=="loaded")return;
  if(!Number.isFinite(rate)||rate<0||rate>30){setError("Enter an assumed annual rate from 0% to 30%.");return;}
  setError("");
  setRun({result:calculatePortfolio(applications,{currentInterestPct:rate,rateShockBps:shock,propertyDropPct:drop}),rate,shock,drop});
 };
 const stale=run!==null&&(run.rate!==rate||run.shock!==shock||run.drop!==drop||status!=="loaded");
 const data=run?.result;
 const maxCount=Math.max(1,...(data?.riskBefore??[]),...(data?.riskAfter??[]));
 return <section className="portfolio-stress" aria-label="Portfolio-wide stress testing">
  <section className="panel pad"><h2>Portfolio-wide scenario</h2>
   <p className="core-note">Runs one scenario across all accessible applications that are Applied, AI Deliberated or Underwriter Approved. Funded and Closed applications are excluded. Calculations use the seller asking price rather than an ML valuation, and a shared assumed interest rate.</p>
   <div className="stress-fields">
    <div className="field"><label htmlFor="portfolio-rate">Assumed annual mortgage rate (%)</label>
     <input id="portfolio-rate" type="number" min="0" max="30" step="0.25" value={Number.isNaN(rate)?"":rate}
      onChange={e=>setRate(e.target.valueAsNumber)}/></div>
    <div className="field"><label htmlFor="portfolio-bps">Interest-rate shock: +{shock} bps</label>
     <input id="portfolio-bps" type="range" min="0" max="500" step="25" value={shock} onChange={e=>setShock(Number(e.target.value))}/></div>
    <div className="field"><label htmlFor="portfolio-downturn">Property market downturn: {drop}%</label>
     <input id="portfolio-downturn" type="range" min="0" max="50" step="5" value={drop} onChange={e=>setDrop(Number(e.target.value))}/></div>
   </div>
   <div className="stress-shortcuts" aria-label="Rate shock presets">{[0,100,200,300].map(n=>
    <button type="button" key={n} className="btn secondary" aria-pressed={shock===n} onClick={()=>setShock(n)}>+{n} bps</button>)}</div>
   <div className="stress-shortcuts" aria-label="Property downturn presets">{[0,10,20,30].map(n=>
    <button type="button" key={n} className="btn secondary" aria-pressed={drop===n} onClick={()=>setDrop(n)}>{n}% drop</button>)}</div>
   {status==="loading"&&<p role="status" className="core-note">Loading authorised applications…</p>}
   {status==="error"&&<p role="alert" className="auth-error">Portfolio data could not be loaded. Refresh applications to retry.</p>}
   {error&&<p role="alert" className="auth-error">{error}</p>}
   <button type="button" className="btn primary" disabled={status!=="loaded"} onClick={runScenario}>Run portfolio stress test</button>
   {stale&&<p className="core-note" role="status">Inputs or application data changed. Run again to update the results.</p>}
  </section>
  {data&&<section aria-label="Portfolio scenario results">
   <div className="panel pad section">
    <h2>Portfolio results <span className="stress-result-sub">+{run.shock} bps · {run.drop}% property downturn · {run.rate.toFixed(2)}% assumed base rate</span></h2>
    <div className="portfolio-metrics">
     <div><span>Applications tested</span><strong>{data.rows.length}</strong></div>
     <div><span>Become unaffordable</span><strong>{data.unaffordable}</strong></div>
     <div><span>LTV above 90%</span><strong>{data.ltvOver90}</strong></div>
     <div><span>LTV above 100%</span><strong>{data.ltvOver100}</strong></div>
    </div>
    <p className="core-note">Accessible applications: {applications.length}. Active at run: {data.activeCount}. Funded/Closed excluded: {data.excludedClosedCount}. Invalid or incomplete active records skipped: {data.skippedIds.length}. Total tested requested principal: {money.format(data.totalLoan)}.</p>
    {data.skippedIds.length>0&&<details><summary>Skipped applications ({data.skippedIds.length})</summary><p className="core-note">{data.skippedIds.join(", ")}</p></details>}
    {data.rows.length===0&&<p className="core-note">No valid active applications available for this scenario.</p>}
   </div>
   {data.rows.length>0&&<>
    <div className="panel pad section">
     <h2>Risk rating distribution</h2>
     <p className="core-note">Number of mortgage applications in each calculated risk tier before and after the scenario.</p>
     <div className="portfolio-chart-legend"><span className="portfolio-key original"/>Original <span className="portfolio-key stressed"/>Stressed</div>
     <div className="portfolio-chart" role="img" aria-label={`Risk tier chart. Original counts: ${data.riskBefore.join(", ")}. Stressed counts: ${data.riskAfter.join(", ")}.`}>
      {[1,2,3,4,5].map((tier,i)=><div className="portfolio-chart-row" key={tier}>
       <strong>Tier {tier}</strong><div className="portfolio-chart-bars">
        <div className="portfolio-chart-track"><div className="portfolio-chart-bar original" style={{width:`${data.riskBefore[i]/maxCount*100}%`}}/><span>{data.riskBefore[i]}</span></div>
        <div className="portfolio-chart-track"><div className="portfolio-chart-bar stressed" style={{width:`${data.riskAfter[i]/maxCount*100}%`}}/><span>{data.riskAfter[i]}</span></div>
       </div>
      </div>)}
     </div>
    </div>
    <div className="panel section live-applications">
     <div className="table-head"><div><h2>Application-level scenario results</h2><p className="live-applications-caption">Read-only local calculations, not official lending decisions</p></div></div>
     <div className="table-wrap"><table><thead><tr><th>Application</th><th>County</th><th>Principal</th><th>Stressed repayment / month</th><th>Stressed DTI</th><th>Stressed LTV</th><th>Original risk</th><th>Stressed risk</th><th>Classification</th></tr></thead>
      <tbody>{data.rows.map(r=><tr key={r.applicationId}><td className="strong">{r.applicationId}</td><td>{r.county}</td><td>{money.format(r.loanAmount)}</td><td>{money.format(r.result.stressedMonthlyPayment)}</td><td>{pct(r.result.stressedDTI)}</td><td>{pct(r.result.stressedLTV)}</td><td>{r.result.originalRiskRating}</td><td>{r.result.stressedRiskRating}</td><td>{r.result.verdict}</td></tr>)}</tbody>
     </table></div>
    </div>
   </>}
   <p className="core-note">These are illustrative calculations from submitted application values. No AI service is called, no Firestore values are updated, and this simulator does not make approval decisions.</p>
  </section>}
 </section>;
}
