export type LoanStatus = "Applied" | "AI Deliberated" | "Underwriter Approved" | "Funded" | "Closed";
export type RiskTier = "Low" | "Medium" | "High";
export interface LoanApplication {
  id: string;
  borrower: string;
  county: string;
  requestedAmount: number;
  propertyValue: number;
  ltv: number;
  risk: RiskTier;
  status: LoanStatus;
  broker: string;
}
/** Entirely fictional demonstration data; not retrieved from AL-Core. */
export const sampleApplications: LoanApplication[] = [
  {id:"AL-1001",borrower:"Sample Borrower A",county:"Kildare",requestedAmount:245000,propertyValue:350000,ltv:70,risk:"Low",status:"Applied",broker:"ed"},
  {id:"AL-1002",borrower:"Sample Borrower B",county:"Dublin",requestedAmount:320000,propertyValue:400000,ltv:80,risk:"Medium",status:"AI Deliberated",broker:"ed"},
  {id:"AL-1003",borrower:"Sample Borrower C",county:"Galway",requestedAmount:280000,propertyValue:310000,ltv:90.3,risk:"High",status:"AI Deliberated",broker:"other-broker"},
  {id:"AL-1004",borrower:"Sample Borrower D",county:"Cork",requestedAmount:190000,propertyValue:290000,ltv:65.5,risk:"Low",status:"Underwriter Approved",broker:"other-broker"}
];
