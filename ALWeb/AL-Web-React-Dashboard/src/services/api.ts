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
export const createBorrowerProfile = (displayName: string) =>
 coreRequest<CoreProfile>("/users/me", {method:"POST",body:JSON.stringify({display_name:displayName.slice(0,30)})});
