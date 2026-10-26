export type LoanStatus = "Applied" | "AI Deliberated" | "Underwriter Approved" | "Funded" | "Closed";
export type RiskTier = "Low" | "Medium" | "High";
export interface LoanApplication {
  id:string;
  borrower:string;
  county:string;
  propertyAddress:string;
  propertyType:string;
  requestedAmount:number;
  propertyValue:number;
  askingPrice:number;
  annualIncome:number;
  annualDebtPayments:number;
  ltv:number;
  risk:RiskTier;
  status:LoanStatus;
  broker:string;
  submittedDate:string;
}
export const loanStatuses:LoanStatus[]=["Applied","AI Deliberated","Underwriter Approved","Funded","Closed"];
export const riskTiers:RiskTier[]=["Low","Medium","High"];
/** Fictional, non-persistent data. No actual borrower records or decisions. */
export const sampleApplications:LoanApplication[]=[
 {id:"AL-1001",borrower:"Sample Borrower A",county:"Kildare",propertyAddress:"Example Road, Naas",propertyType:"Semi-detached",requestedAmount:245000,propertyValue:350000,askingPrice:365000,annualIncome:85000,annualDebtPayments:4800,ltv:70,risk:"Low",status:"Applied",broker:"ed",submittedDate:"2026-10-18"},
 {id:"AL-1002",borrower:"Sample Borrower B",county:"Dublin",propertyAddress:"Example Avenue, Dublin 12",propertyType:"Terraced",requestedAmount:320000,propertyValue:400000,askingPrice:425000,annualIncome:96000,annualDebtPayments:7200,ltv:80,risk:"Medium",status:"AI Deliberated",broker:"ed",submittedDate:"2026-10-19"},
 {id:"AL-1003",borrower:"Sample Borrower C",county:"Galway",propertyAddress:"Example Crescent, Galway",propertyType:"Apartment",requestedAmount:280000,propertyValue:310000,askingPrice:340000,annualIncome:75000,annualDebtPayments:9000,ltv:90.3,risk:"High",status:"AI Deliberated",broker:"other-broker",submittedDate:"2026-10-21"},
 {id:"AL-1004",borrower:"Sample Borrower D",county:"Cork",propertyAddress:"Example Park, Cork",propertyType:"Detached",requestedAmount:190000,propertyValue:290000,askingPrice:295000,annualIncome:68000,annualDebtPayments:3000,ltv:65.5,risk:"Low",status:"Underwriter Approved",broker:"other-broker",submittedDate:"2026-10-22"},
 {id:"AL-1005",borrower:"Sample Borrower E",county:"Meath",propertyAddress:"Example Close, Navan",propertyType:"Bungalow",requestedAmount:235000,propertyValue:300000,askingPrice:310000,annualIncome:82000,annualDebtPayments:4500,ltv:78.3,risk:"Medium",status:"Funded",broker:"ed",submittedDate:"2026-10-23"},
 {id:"AL-1006",borrower:"Sample Borrower F",county:"Wicklow",propertyAddress:"Example Grove, Bray",propertyType:"Apartment",requestedAmount:160000,propertyValue:260000,askingPrice:260000,annualIncome:59000,annualDebtPayments:2400,ltv:61.5,risk:"Low",status:"Closed",broker:"other-broker",submittedDate:"2026-10-24"}
];
/** UI-only restriction. The real API must enforce broker assignments server-side. */
export function visibleApplications(role:"broker"|"underwriter",username:string,items:LoanApplication[]=sampleApplications){
 return role==="broker"?items.filter(item=>item.broker===username):items;
}
