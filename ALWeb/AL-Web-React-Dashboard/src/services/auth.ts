import {
 createUserWithEmailAndPassword,
 onAuthStateChanged,
 signInWithEmailAndPassword,
 signOut,
 type User,
} from "firebase/auth";
import { getFirebaseAuth } from "../firebase/firebase";
import type { PortalRole } from "../types/roles";
import {createCoreProfile,getCoreProfile,ApiError} from "./api";

/**
 * UI-only development role mapping. No real authorization is granted here.
 * Existing test accounts retain their preconfigured demo roles.
 * Newly registered test accounts store their selected role in this browser,
 * keyed by Firebase UID. It does not follow users to other devices.
 */
const demoAccounts: Record<string, { username: string; role: PortalRole }> = {
 "borrower@test.agenticlender.ie": { username: "borrower-test", role: "borrower" },
 "broker@test.agenticlender.ie": { username: "ed", role: "broker" },
 "underwriter@test.agenticlender.ie": { username: "student", role: "underwriter" },
};
const demoRoleKey = (uid: string) => `alweb-demo-role:${uid}`;
const demoUsername: Record<PortalRole, string> = {
 borrower: "borrower-test",
 broker: "ed",
 underwriter: "student",
};
const validRole = (value: string | null): value is PortalRole =>
 value === "borrower" || value === "broker" || value === "underwriter";

export type AuthUser = { uid: string; email: string; username: string; role: PortalRole };

function mapFirebaseUser(user: User | null): AuthUser | null {
 if (!user?.email) return null;
 const configured = demoAccounts[user.email.trim().toLowerCase()];
 if (configured) return { uid: user.uid, email: user.email, ...configured };

 // Development-only UI selection. NEVER trust localStorage for access control.
 let storedRole: string | null = null;
 try {
  storedRole = window.localStorage.getItem(demoRoleKey(user.uid));
 } catch {
  return null;
 }
 if (!validRole(storedRole)) return null;
 return { uid: user.uid, email: user.email, role: storedRole, username: demoUsername[storedRole] };
}

function readableAuthError(error: unknown): Error {
 const code = typeof error === "object" && error !== null && "code" in error ? String(error.code) : "";
 switch (code) {
  case "auth/invalid-email": return new Error("Enter a valid email address.");
  case "auth/invalid-credential":
  case "auth/wrong-password":
  case "auth/user-not-found": return new Error("Incorrect email or password.");
  case "auth/email-already-in-use": return new Error("An account already exists for that email address. Please sign in.");
  case "auth/weak-password": return new Error("Choose a stronger password (at least 6 characters).");
  case "auth/too-many-requests": return new Error("Too many attempts. Please try again later.");
  case "auth/network-request-failed": return new Error("Unable to contact Firebase. Check your internet connection.");
  case "auth/operation-not-allowed": return new Error("Email/password authentication is disabled in Firebase.");
  default: return error instanceof Error ? error : new Error("Firebase authentication failed.");
 }
}

/** Prefer the backend profile; retain the existing demo mapping for legacy accounts
 * and the missing-profile recovery screen. Backend endpoints enforce permissions. */
async function resolveUser(user: User | null): Promise<AuthUser | null> {
 if (!user?.email) return null;
 try {
  const profile = await getCoreProfile();
  if (profile.uid !== user.uid || !validRole(profile.role)) throw new Error("Invalid backend profile.");
  return {uid:user.uid,email:user.email,role:profile.role,username:profile.display_name || demoAccounts[user.email.toLowerCase()]?.username || user.email};
 } catch (error) {
  if (error instanceof ApiError && error.status === 404) return mapFirebaseUser(user);
  throw error;
 }
}

/** Authenticate with Firebase, then resolve the AL-Core profile. */
export async function login(email: string, password: string): Promise<AuthUser> {
 const auth = getFirebaseAuth();
 try {
  const result = await signInWithEmailAndPassword(auth, email.trim(), password);
  const mapped = await resolveUser(result.user);
  if (!mapped) {
   await signOut(auth);
   throw new Error("No backend profile or local recovery role is available for this account. Contact the project team.");
  }
  return mapped;
 } catch (error) {
  throw readableAuthError(error);
 }
}

/** Create a Firebase identity, then create its persistent AL-Core profile.
 * Role selection remains development-only until the backend restricts it.
 */
export async function register(email: string, password: string, selectedRole: PortalRole, firstName: string, lastName: string, phoneNumber: string): Promise<AuthUser> {
 if (!validRole(selectedRole)) throw new Error("Select a valid test role.");
 if (!firstName.trim() || firstName.trim().length > 30 || !lastName.trim() || lastName.trim().length > 50)
  throw new Error("Enter a first name (1–30 characters) and last name (1–50 characters).");
 if (!phoneNumber.trim() || phoneNumber.trim().length > 20)
  throw new Error("Enter a phone number of up to 20 characters.");
 const auth = getFirebaseAuth();
 let created: User;
 try {
  created = (await createUserWithEmailAndPassword(auth, email.trim(), password)).user;
 } catch (error) {
  throw readableAuthError(error);
 }
 // Remember the test role only to support recovery of an incomplete signup.
 try { window.localStorage.setItem(demoRoleKey(created.uid), selectedRole); } catch { /* backend profile is authoritative */ }
 try {
  const profile = await createCoreProfile(firstName,lastName,selectedRole,phoneNumber);
  if (profile.uid !== created.uid || !validRole(profile.role)) throw new Error("Invalid backend profile.");
  return {uid:created.uid,email:created.email ?? email.trim(),role:profile.role,username:profile.display_name || firstName.trim()};
 } catch (error) {
  // The POST might have succeeded before a network failure. Never delete the Firebase account.
  // If a valid profile exists, use it instead of repeating profile creation.
  try {
   const profile = await getCoreProfile();
   if (profile.uid === created.uid && validRole(profile.role))
    return {uid:created.uid,email:created.email ?? email.trim(),role:profile.role,username:profile.display_name || firstName.trim()};
  } catch { /* Preserve the original error for the user. */ }
  await signOut(auth);
  throw new Error("Firebase account created, but AL-Core profile creation could not be confirmed. Sign in with the same email and use AL-Core connection status to complete the profile if missing. " + (error instanceof Error ? error.message : ""));
 }
}

/** Wait for Firebase to restore its session after a browser refresh. */
export async function getCurrentUser(): Promise<AuthUser | null> {
 const auth = getFirebaseAuth();
 await auth.authStateReady();
 return resolveUser(auth.currentUser);
}

export async function logout(): Promise<void> {
 await signOut(getFirebaseAuth());
}

export function observeCurrentUser(callback: (user: AuthUser | null) => void): () => void {
 return onAuthStateChanged(getFirebaseAuth(), user => { void resolveUser(user).then(callback).catch(() => callback(null)); });
}
