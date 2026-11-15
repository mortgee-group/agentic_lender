import { getFirebaseAuth } from "../firebase/firebase";
import type { PortalRole } from "../types/roles";

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ||
 "https://agentic-lender-525895646792.europe-west2.run.app").replace(/\/+$/, "");

export class ApiError extends Error {
 constructor(public status: number, public detail: string) {
  super(`AL-Core HTTP ${status}: ${detail}`);
  this.name = "ApiError";
 }
}
async function errorDetail(response: Response): Promise<string> {
 const body: unknown = await response.json().catch(() => null);
 if (body && typeof body === "object" && "detail" in body) {
  const detail = (body as { detail: unknown }).detail;
  if (typeof detail === "string") return detail;
  // FastAPI uses an array for Pydantic validation errors (HTTP 422).
  if (Array.isArray(detail)) {
   return detail.map(item => {
    if (!item || typeof item !== "object") return String(item);
    const issue = item as { loc?: unknown; msg?: unknown; type?: unknown };
    const location = Array.isArray(issue.loc) ? issue.loc.join(".") : "request";
    return `${location}: ${String(issue.msg ?? issue.type ?? "Validation error")}`;
   }).join("; ");
  }
 }
 return response.statusText || `HTTP ${response.status}`;
}
export async function coreRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
 const user = getFirebaseAuth().currentUser;
 if (!user) throw new Error("Sign in to Firebase first.");
 const token = await user.getIdToken();
 const headers = new Headers(options.headers);
 headers.set("Authorization", `Bearer ${token}`);
 if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
 const response = await fetch(`${API_BASE_URL}${path}`, {...options, headers});
 if (!response.ok) throw new ApiError(response.status, await errorDetail(response));
 return response.json() as Promise<T>;
}
export type CoreProfile = { uid: string; email: string; role: PortalRole; display_name: string };
export async function checkBackendHealth(): Promise<boolean> {
 const response = await fetch(`${API_BASE_URL}/health`);
 if (!response.ok) throw new ApiError(response.status, await errorDetail(response));
 const body = await response.json() as {status:string};
 return body.status === "ok";
}
export const verifyCoreAuthentication = () => coreRequest<{uid:string}>("/auth/me");
export const getCoreProfile = () => coreRequest<CoreProfile>("/users/me");
export const getCoreRoleAccess = (role: PortalRole) =>
 coreRequest<{uid:string;role:PortalRole;message:string}>(`/${role}/dashboard`);
/**
 * Temporary AL-Core development registration contract. Backend currently
 * accepts a selected role; this must be restricted before production.
 * Never take a role from an arbitrary request when authorising data access.
 */
export const createCoreProfile = (firstName: string, lastName: string, role: PortalRole) =>
 coreRequest<CoreProfile>("/users/me", {
  method: "POST",
  body: JSON.stringify({
   first_name: firstName.trim(),
   last_name: lastName.trim(),
   role,
  }),
 });

/** Current AL-Core GET /applications response; fields may be null until AI analysis. */
export type CoreApplication = {
 applicationId: string;
 borrowerId: string;
 brokerId: string | null;
 property: {
  county: string;
  floor_area: number;
  bedrooms: number;
  bathrooms: number;
  year_built: number;
  property_type: string;
  ber_rating: string;
 };
 householdAnnualGrossIncome: number;
 existingDebtLiabilities: number;
 deposit: number;
 requestedMortgageTerm: number;
 sellerAskingPrice: number;
 requestedLoanAmount: number | null;
 estimatedLTV: number | null;
 riskTier: "Tier 1" | "Tier 2" | "Tier 3" | "Tier 4" | "Tier 5" | null;
 approvalLikelihood: "High" | "Medium" | "Low" | null;
 lifecycleStatus: "APPLIED" | "AI_DELIBERATED" | "UNDERWRITER_APPROVED" | "FUNDED" | "CLOSED";
 createdAt: string;
 updatedAt: string;
 submittedAt: string | null;
};
export async function getCoreApplications(): Promise<CoreApplication[]> {
 const data = await coreRequest<unknown>("/applications");
 if (!Array.isArray(data)) throw new Error("AL-Core returned an unexpected applications format (expected an array).");
 return data as CoreApplication[];
}

/** Fields required by AL-Core LoanApplicationCreate (current main). */
export type CoreApplicationCreate = {
 householdAnnualGrossIncome: number;
 existingDebtLiabilities: number;
 deposit: number;
 requestedMortgageTerm: number;
 sellerAskingPrice: number;
 property: {
  county: string;
  floor_area: number;
  bedrooms: number;
  bathrooms: number;
  year_built: number;
  property_type: "APARTMENT"|"DETACHED"|"SEMI_DETACHED"|"TERRACED"|"BUNGALOW"|"TOWN_HOUSE"|"COMMERCIAL"|"OTHER";
  ber_rating: "A1"|"A2"|"A3"|"B1"|"B2"|"B3"|"C1"|"C2"|"C3"|"D1"|"D2"|"E1"|"E2"|"F"|"G"|"EXEMPT";
 };
};
export const createCoreApplication = (data: CoreApplicationCreate) =>
 coreRequest<{applicationId:string;message:string}>("/applications",{
  method:"POST",body:JSON.stringify(data),
 });

/** Fetch one application using AL-Core's own record-level permission check. */
export const getCoreApplication = (applicationId: string) =>
 coreRequest<CoreApplication>(`/applications/${encodeURIComponent(applicationId)}`);

/** The AL-Core AI routes forward responses from AL-AI.
 * The assessment response is a valuation/affordability estimate; the
 * underwriting response contains a decision and detailed audit output.
 */
export type AssessmentResponse = {
 predicted_value: number;
 overvaluation_pct: number;
 ltv: number;
 dti: number;
 stress_tested_repayment: number;
 max_borrowing_capacity: number;
};
export type UnderwritingResponse = {
 applicationId: string;
 decision: string;
 rationale: string;
 ltv: number;
 dti: number;
 risk_rating: number;
 conditions: string[];
 credit_memo: string;
 agent_trails: string | unknown[];
 agent_outputs: Record<string, unknown>;
 estimatedLTV: number;
 DTI: number;
 approvalLikelihood: "High" | "Medium" | "Low";
 riskTier: "Tier 1" | "Tier 2" | "Tier 3" | "Tier 4" | "Tier 5";
 lifecycleStatus: string;
};
/** These calls can persist AI results in Firestore; only invoke on direct user action. */
export const requestCoreAssessment = (applicationId: string) =>
 coreRequest<AssessmentResponse>(`/applications/${encodeURIComponent(applicationId)}/assess`,{method:"POST"});
export const requestCoreUnderwriting = (applicationId: string) =>
 coreRequest<UnderwritingResponse>(`/applications/${encodeURIComponent(applicationId)}/underwrite`,{method:"POST"});
