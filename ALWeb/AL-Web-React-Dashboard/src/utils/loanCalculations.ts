import type {LoanApplication} from "../data/sampleApplications";
export const euro=(value:number)=>new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0}).format(value);
export const percent=(value:number)=>new Intl.NumberFormat("en-IE",{maximumFractionDigits:1}).format(value)+"%";
/** This is an annual debt-payments / gross annual income illustration, not a lending decision. */
export const demoDebtRatio=(application:LoanApplication)=>application.annualIncome>0?
 application.annualDebtPayments/application.annualIncome*100:0;
export const valuationGap=(application:LoanApplication)=>application.askingPrice-application.propertyValue;
