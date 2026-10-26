import type {LoanApplication} from "./sampleApplications";
export type AgentId="affordability"|"valuation"|"market"|"strategist";
export type AgentAssessment={id:AgentId;name:string;finding:string;evidence:string[];status:"Illustrative finding"|"Illustrative recommendation"};
export type AuditRecord={applicationId:string;recommendation:"Approve"|"Conditionally Approve"|"Decline";consensus:string;agents:AgentAssessment[];events:{step:string;detail:string}[]};
const humanCurrency=(n:number)=>new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0}).format(n);
/** Explicitly synthetic examples, not LLM output, model inference, or actual approvals. */
export function exampleAudit(application:LoanApplication):AuditRecord|null{
 if(application.status==="Applied")return null;
 const elevated=application.risk==="High";
 const recommendation=elevated?"Decline":application.risk==="Medium"?"Conditionally Approve":"Approve";
 return {
  applicationId:application.id,
  recommendation,
  consensus:elevated?"Illustrative agents highlight elevated collateral risk requiring manual review.":application.risk==="Medium"?"Illustrative agents support proceeding only after additional income and collateral checks.":"Illustrative agents identify no major warning in this synthetic example.",
  agents:[
   {id:"affordability",name:"Affordability Agent",status:"Illustrative finding",finding:elevated?"Further affordability evidence required.":"Income and existing debt are recorded for review.",evidence:[`Annual gross income: ${humanCurrency(application.annualIncome)}`,`Existing annual debt payments: ${humanCurrency(application.annualDebtPayments)}`,"No regulatory affordability decision has been calculated."]},
   {id:"valuation",name:"Valuation Agent",status:"Illustrative finding",finding:elevated?"Loan-to-value is elevated in this example.":"Compare the requesting amount against the indicative property estimate.",evidence:[`Illustrative property value: ${humanCurrency(application.propertyValue)}`,`Seller asking price: ${humanCurrency(application.askingPrice)}`,`Sample LTV: ${application.ltv.toFixed(1)}%`]},
   {id:"market",name:"Market & Macro Researcher",status:"Illustrative finding",finding:"Market context is a placeholder; no current financial research has been performed.",evidence:["Market and central-bank rates not connected.","No external source or live search has been consulted.","This item awaits the AL-AI service."]},
   {id:"strategist",name:"Underwriting Strategist",status:"Illustrative recommendation",finding:`Synthetic recommendation: ${recommendation}.`,evidence:["Based on fabricated sample application attributes.","No signed credit memorandum has been generated.","A bank official must make any real decision."]}
  ],
  events:[
   {step:"Application details",detail:"Fictional borrower, loan and property fields loaded from frontend samples."},
   {step:"Agent assessments",detail:"Predefined illustrative explanations generated for the interface demonstration."},
   {step:"Consensus view",detail:"Synthetic recommendation rendered; no LLM or external inference was invoked."}
  ]
 };
}
