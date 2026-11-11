import { onAuthStateChanged, signInWithEmailAndPassword, signOut, type User } from "firebase/auth";
import { getFirebaseAuth } from "../firebase/firebase";
import type { PortalRole } from "../types/roles";

/**
 * Temporary demo-only role assignment.
 * Firebase validates email/password; this mapping only selects a UI for
 * three explicitly created fictional test users. It is NOT authorisation.
 * Replace with trusted claims / backend-verified Firestore profiles.
 */
const demoAccounts: Record<string, { username: string; role: PortalRole }> = {
 "borrower@test.agenticlender.ie": { username: "borrower-test", role: "borrower" },
 "broker@test.agenticlender.ie": { username: "ed", role: "broker" },
 "underwriter@test.agenticlender.ie": { username: "student", role: "underwriter" },
};

export type AuthUser = { uid: string; email: string; username: string; role: PortalRole };

function mapFirebaseUser(user: User | null): AuthUser | null {
 if (!user?.email) return null;
 const account = demoAccounts[user.email.trim().toLowerCase()];
 return account ? { uid: user.uid, email: user.email, ...account } : null;
}

function readableAuthError(error: unknown): Error {
 const code = typeof error === "object" && error !== null && "code" in error ? String(error.code) : "";
 switch (code) {
  case "auth/invalid-email": return new Error("Enter a valid email address.");
  case "auth/invalid-credential":
  case "auth/wrong-password":
  case "auth/user-not-found": return new Error("Incorrect email or password.");
  case "auth/too-many-requests": return new Error("Too many attempts. Please try again later.");
  case "auth/network-request-failed": return new Error("Unable to contact Firebase. Check your internet connection.");
  case "auth/operation-not-allowed": return new Error("Email/password sign-in is not enabled in Firebase.");
  default: return error instanceof Error ? error : new Error("Unable to sign in to Firebase.");
 }
}

/** Authenticate with Firebase, then resolve the fictional UI role. */
export async function login(email: string, password: string): Promise<AuthUser> {
 const auth = getFirebaseAuth();
 try {
  const result = await signInWithEmailAndPassword(auth, email.trim(), password);
  const mapped = mapFirebaseUser(result.user);
  if (!mapped) {
   await signOut(auth);
   throw new Error("This account has no Agentic Lender test role. Contact the project team.");
  }
  return mapped;
 } catch (error) {
  throw readableAuthError(error);
 }
}

/** Firebase restores its persisted session before this promise resolves. */
export async function getCurrentUser(): Promise<AuthUser | null> {
 const auth = getFirebaseAuth();
 await auth.authStateReady();
 return mapFirebaseUser(auth.currentUser);
}

/** Used when the user explicitly logs out. */
export async function logout(): Promise<void> {
 await signOut(getFirebaseAuth());
}

/**
 * Optional observer for future profile integration.
 * Firebase SDK controls the authenticated session, never a local demo cookie.
 */
export function observeCurrentUser(callback: (user: AuthUser | null) => void): () => void {
 return onAuthStateChanged(getFirebaseAuth(), user => callback(mapFirebaseUser(user)));
}
