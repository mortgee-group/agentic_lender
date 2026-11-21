import type {CoreApplication} from "../services/api";
import {calculateStress,type StressResult,type StressScenario} from "./stressTesting";

/** Portfolio mode is intentionally local: no AI inference and no persisted decisions. */
export const ACTIVE_STATUSES=["APPLIED","AI_DELIBERATED","UNDERWRITER_APPROVED"] as const;
export type PortfolioRow={applicationId:string;county:string;loanAmount:number;result:StressResult};
export type PortfolioResult={
 rows:PortfolioRow[];activeCount:number;excludedClosedCount:number;skippedIds:string[];
 unaffordable:number;marginal:number;affordable:number;ltvOver90:number;ltvOver100:number;
 riskBefore:number[];riskAfter:number[];totalLoan:number;
};
/** All entries use the same explicitly assumed rate and seller asking price proxy. */
export function calculatePortfolio(
 applications:CoreApplication[],
 assumptions:Pick<StressScenario,"currentInterestPct"|"rateShockBps"|"propertyDropPct">
):PortfolioResult{
 const active=applications.filter(a=>(ACTIVE_STATUSES as readonly string[]).includes(a.lifecycleStatus));
 const rows:PortfolioRow[]=[];
 const skippedIds:string[]=[];
 for(const a of active){
  const loan=typeof a.requestedLoanAmount==="number"?a.requestedLoanAmount:a.sellerAskingPrice-a.deposit;
  const input:StressScenario={
   loanAmount:loan,propertyValue:a.sellerAskingPrice,annualIncome:a.householdAnnualGrossIncome,
   existingAnnualDebt:a.existingDebtLiabilities,mortgageTermYears:a.requestedMortgageTerm,...assumptions,
  };
  try{
   const result=calculateStress(input);
   rows.push({applicationId:a.applicationId,county:a.property?.county??"—",loanAmount:loan,result});
  }catch{
   skippedIds.push(a.applicationId);
  }
 }
 return {
  rows,activeCount:active.length,excludedClosedCount:applications.length-active.length,skippedIds,
  affordable:rows.filter(r=>r.result.verdict==="AFFORDABLE").length,
  marginal:rows.filter(r=>r.result.verdict==="MARGINAL").length,
  unaffordable:rows.filter(r=>r.result.verdict==="UNAFFORDABLE").length,
  ltvOver90:rows.filter(r=>r.result.stressedLTV>90).length,
  ltvOver100:rows.filter(r=>r.result.stressedLTV>100).length,
  riskBefore:[1,2,3,4,5].map(n=>rows.filter(r=>r.result.originalRiskRating===n).length),
  riskAfter:[1,2,3,4,5].map(n=>rows.filter(r=>r.result.stressedRiskRating===n).length),
  totalLoan:rows.reduce((sum,r)=>sum+r.loanAmount,0),
 };
}
