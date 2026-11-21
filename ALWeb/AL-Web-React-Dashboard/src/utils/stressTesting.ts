/** Standalone frontend scenario calculator.
 * The amortisation, DTI, risk score and verdict thresholds mirror AL-AI's
 * app/stress_test.py as reviewed on 21 Nov 2026. Unlike AL-AI, this uses an
 * explicitly supplied property value, NOT a Random Forest ML valuation.
 * Existing debt liabilities are treated as ANNUAL debt service.
 */
export type StressScenario = {
 loanAmount: number;
 propertyValue: number;
 annualIncome: number;
 existingAnnualDebt: number;
 mortgageTermYears: number;
 currentInterestPct: number;
 rateShockBps: number;
 propertyDropPct: number;
};
export type StressResult = {
 originalMonthlyPayment:number; stressedMonthlyPayment:number;
 originalDTI:number; stressedDTI:number;
 originalLTV:number; stressedLTV:number;
 originalRiskRating:number; stressedRiskRating:number;
 stressedPropertyValue:number;
 verdict:"AFFORDABLE"|"MARGINAL"|"UNAFFORDABLE";
};
export function monthlyRepayment(principal:number,ratePct:number,years:number):number{
 if(principal<=0)return 0;
 const monthlyRate=ratePct/1200;
 const months=years*12;
 if(monthlyRate===0)return principal/months;
 return principal*monthlyRate/(1-Math.pow(1+monthlyRate,-months));
}
export function riskRating(dti:number,ltv:number):number{
 let score=1;
 if(dti>35)score++;
 if(dti>45)score++;
 if(ltv>80)score++;
 if(ltv>90)score++;
 return Math.min(score,5);
}
export function calculateStress(s:StressScenario):StressResult{
 const values=[s.loanAmount,s.propertyValue,s.annualIncome,s.existingAnnualDebt,
  s.mortgageTermYears,s.currentInterestPct,s.rateShockBps,s.propertyDropPct];
 if(values.some(n=>!Number.isFinite(n)))throw new Error("All fields must contain valid numbers.");
 if(s.loanAmount<=0||s.propertyValue<=0||s.annualIncome<=0||s.mortgageTermYears<1||s.mortgageTermYears>40
   ||s.existingAnnualDebt<0||s.currentInterestPct<0||s.currentInterestPct>30
   ||s.rateShockBps<0||s.rateShockBps>500||s.propertyDropPct<0||s.propertyDropPct>50)
  throw new Error("Check the mortgage, income, term, interest rate and scenario values.");
 const originalMonthlyPayment=monthlyRepayment(s.loanAmount,s.currentInterestPct,s.mortgageTermYears);
 const stressedMonthlyPayment=monthlyRepayment(s.loanAmount,s.currentInterestPct+s.rateShockBps/100,s.mortgageTermYears);
 const stressedPropertyValue=s.propertyValue*(1-s.propertyDropPct/100);
 const originalDTI=(originalMonthlyPayment*12+s.existingAnnualDebt)/s.annualIncome*100;
 const stressedDTI=(stressedMonthlyPayment*12+s.existingAnnualDebt)/s.annualIncome*100;
 const originalLTV=s.loanAmount/s.propertyValue*100;
 const stressedLTV=s.loanAmount/stressedPropertyValue*100;
 const verdict=stressedDTI>50||stressedLTV>100?"UNAFFORDABLE"
  :stressedDTI>40||stressedLTV>90?"MARGINAL":"AFFORDABLE";
 return {
  originalMonthlyPayment,stressedMonthlyPayment,originalDTI,stressedDTI,
  originalLTV,stressedLTV,
  originalRiskRating:riskRating(originalDTI,originalLTV),
  stressedRiskRating:riskRating(stressedDTI,stressedLTV),
  stressedPropertyValue,verdict,
 };
}
