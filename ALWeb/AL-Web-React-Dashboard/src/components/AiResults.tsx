import type {AssessmentResponse,UnderwritingResponse} from "../services/api";

type PreviewData = {assessment:AssessmentResponse;decision:UnderwritingResponse};
const euro=new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0});
const money=(value:number)=>euro.format(value);
const percent=(value:number)=>`${value.toFixed(1)}%`;
const valueText=(value:unknown):string=>{
 if(value===null||value===undefined)return "Not provided";
 if(typeof value==="string"||typeof value==="boolean")return String(value);
 if(typeof value==="number")return Number.isFinite(value)?String(value):"Not provided";
 if(Array.isArray(value))return value.map(valueText).join("; ");
 return JSON.stringify(value,null,2);
};
const displayKey=(key:string)=>key.replace(/_/g," ").replace(/([a-z])([A-Z])/g,"$1 $2").replace(/\b\w/g,letter=>letter.toUpperCase());
const agentNames:Record<string,string>={
 affordability:"Affordability Agent",
 valuation:"Valuation Agent",
 market_research:"Market & Macro Researcher",
 strategist:"Underwriting Strategist",
 underwriting:"Underwriting Strategist",
};

export const exampleAIResponse:PreviewData={
 assessment:{
  predicted_value:320000,
  overvaluation_pct:9.38,
  ltv:87.5,
  dti:34.2,
  stress_tested_repayment:1670,
  max_borrowing_capacity:297500,
 },
 decision:{
  applicationId:"EXAMPLE-ONLY",
  decision:"CONDITIONALLY APPROVE",
  rationale:"Illustrative outcome: the fictional loan meets some affordability checks but requires additional supporting documentation.",
  ltv:87.5,
  dti:34.2,
  risk_rating:3,
  conditions:["Provide supporting income documents","Confirm an independent property valuation"],
  credit_memo:"EXAMPLE ONLY — Fictional memorandum for demonstrating the user interface. This is not an actual AI assessment, underwriting decision or financial recommendation.",
  agent_trails:"Example committee review only. No AI engine was run.",
  agent_outputs:{
   affordability:{dti:34.2,stress_tested_repayment:1670,max_borrowing_capacity:297500,affordability_verdict:"Marginal",notes:"Illustrative affordability explanation."},
   valuation:{predicted_value:320000,overvaluation_pct:9.38,ltv:87.5,collateral_risk:"Medium",notes:"Illustrative collateral valuation."},
   market_research:{ecb_rate:3.5,summary:"Fictional market conditions for layout testing — not a live ECB rate."},
  },
  estimatedLTV:87.5,
  DTI:34.2,
  approvalLikelihood:"Medium",
  riskTier:"Tier 3",
  lifecycleStatus:"AI_DELIBERATED",
 },
};

const Field=({label,value}:{label:string;value:string|number})=>
 <div className="detail-row"><span>{label}</span><strong>{value}</strong></div>;

export default function AiResults({assessment,decision,example=false}:{
 assessment:AssessmentResponse|null;decision:UnderwritingResponse|null;example?:boolean;
}){
 if(!assessment&&!decision)return null;
 return <div className="ai-results-layout" aria-label={example?"Fictional AI results preview":"AI API results"}>
  <div className={example?"notice ai-results-banner":"ai-results-banner"}>
   <strong>{example?"EXAMPLE DATA — NO AI INFERENCE":"AL-CORE API RESPONSE"}</strong>
   <span>{example
    ?" All numbers, recommendations and agent findings below are fabricated to test the interface. They are not linked to the open mortgage application."
    :" Results were returned by AL-Core during this session. AI recommendations are not human approval or a final lending decision."}</span>
  </div>
  {assessment&&<section className="ai-live-results" aria-label="Property valuation and affordability">
   <h3>Property valuation & affordability</h3>
   <div className="ai-result-grid">
    <Field label="ML-predicted property value" value={money(assessment.predicted_value)}/>
    <Field label="Asking price above model estimate" value={percent(assessment.overvaluation_pct)}/>
    <Field label="Estimated loan-to-value" value={percent(assessment.ltv)}/>
    <Field label="Debt-to-income" value={percent(assessment.dti)}/>
    <Field label="Stress-tested monthly repayment" value={money(assessment.stress_tested_repayment)}/>
    <Field label="Maximum borrowing capacity" value={money(assessment.max_borrowing_capacity)}/>
   </div>
  </section>}
  {decision&&<>
   <section className="ai-live-results" aria-label="Underwriting recommendation">
    <h3>Underwriting recommendation</h3>
    <div className="ai-recommendation">
     <div><span className="eyebrow">AI recommendation — not final approval</span><strong>{decision.decision}</strong></div>
     <div><span className="eyebrow">Risk classification</span><strong>{decision.riskTier}</strong></div>
     <div><span className="eyebrow">Approval likelihood category</span><strong>{decision.approvalLikelihood}</strong></div>
    </div>
    <div className="ai-result-grid">
     <Field label="Estimated loan-to-value" value={percent(decision.estimatedLTV)}/>
     <Field label="Debt-to-income" value={percent(decision.DTI)}/>
     <Field label="Application state returned" value={decision.lifecycleStatus}/>
    </div>
    <h3>Decision rationale</h3><p className="ai-credit-memo">{decision.rationale}</p>
    <h3>Conditions</h3>
    {decision.conditions.length?<ul>{decision.conditions.map((text,i)=><li key={i}>{text}</li>)}</ul>
     :<p>No conditions supplied.</p>}
   </section>
   <section className="ai-live-results" aria-label="Agent deliberation">
    <h3>Agent deliberation</h3>
    <p className="core-note">Individual model outputs supplied by the AL-AI response. Expand an agent to inspect its findings.</p>
    {Object.keys(decision.agent_outputs??{}).length===0&&<p>No individual agent outputs supplied.</p>}
    <div className="ai-agent-panels">
     {Object.entries(decision.agent_outputs??{}).map(([key,data])=><details key={key} className="ai-agent-panel">
      <summary>{agentNames[key]??displayKey(key)}</summary>
      {data&&typeof data==="object"&&!Array.isArray(data)
       ?Object.entries(data).map(([field,value])=>
        <div key={field} className="ai-agent-line"><strong>{displayKey(field)}</strong><span>{valueText(value)}</span></div>)
       :<p className="ai-credit-memo">{valueText(data)}</p>}
     </details>)}
    </div>
    {decision.agent_trails&&<details className="ai-agent-panel">
     <summary>Raw decision trail</summary><pre className="ai-raw-trail">{valueText(decision.agent_trails)}</pre>
    </details>}
   </section>
   <section className="ai-live-results" aria-label="Credit memorandum">
    <h3>Credit memorandum</h3>
    <p className="ai-credit-memo">{decision.credit_memo}</p>
   </section>
  </>}
 </div>;
}
