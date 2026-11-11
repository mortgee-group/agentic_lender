import {
 createUserWithEmailAndPassword,
 onAuthStateChanged,
 signInWithEmailAndPassword,
 signOut,
 type User,
} from "firebase/auth";
import { getFirebaseAuth } from "../firebase/firebase";
import type { PortalRole } from "../types/roles";

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

/** Authenticate with Firebase, then resolve the temporary demo dashboard. */
export async function login(email: string, password: string): Promise<AuthUser> {
 const auth = getFirebaseAuth();
 try {
  const result = await signInWithEmailAndPassword(auth, email.trim(), password);
  const mapped = mapFirebaseUser(result.user);
  if (!mapped) {
   await signOut(auth);
   throw new Error("No local demo role is available for this account on this browser. Contact the project team.");
  }
  return mapped;
 } catch (error) {
  throw readableAuthError(error);
 }
}

/**
 * Create a Firebase user and remember their requested demo UI role locally.
 * The role is not saved as a trusted Firebase claim or Firestore permission.
 */
export async function register(email: string, password: string, selectedRole: PortalRole): Promise<AuthUser> {
 if (!validRole(selectedRole)) throw new Error("Select a valid demo role.");
 const auth = getFirebaseAuth();
 let created: User;
 try {
  const credential = await createUserWithEmailAndPassword(auth, email.trim(), password);
  created = credential.user;
 } catch (error) {
  throw readableAuthError(error);
 }
 try {
  window.localStorage.setItem(demoRoleKey(created.uid), selectedRole);
 } catch {
  await signOut(auth);
  throw new Error("The account was created in Firebase, but this browser could not save its demo role. Enable local storage and contact the project team before retrying.");
 }
 const mapped = mapFirebaseUser(created);
 if (!mapped) {
  await signOut(auth);
  throw new Error("Account created, but the demo role could not be loaded.");
 }
 return mapped;
}

/** Wait for Firebase to restore its session after a browser refresh. */
export async function getCurrentUser(): Promise<AuthUser | null> {
 const auth = getFirebaseAuth();
 await auth.authStateReady();
 return mapFirebaseUser(auth.currentUser);
}

export async function logout(): Promise<void> {
 await signOut(getFirebaseAuth());
}

export function observeCurrentUser(callback: (user: AuthUser | null) => void): () => void {
 return onAuthStateChanged(getFirebaseAuth(), user => callback(mapFirebaseUser(user)));
}
