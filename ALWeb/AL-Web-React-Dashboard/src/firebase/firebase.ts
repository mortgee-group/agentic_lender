import { getApp, getApps, initializeApp, type FirebaseApp, type FirebaseOptions } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

/**
 * Firebase Web SDK setup for AL-Web.
 *
 * Keep using the existing local demo login until the Firebase Authentication
 * integration has been implemented and tested. VITE_ values are embedded in
 * the browser bundle and must never contain private server credentials.
 */
function configFromEnvironment(): FirebaseOptions {
 const env = import.meta.env;
 const config: FirebaseOptions = {
  apiKey: env.VITE_FIREBASE_API_KEY,
  authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: env.VITE_FIREBASE_APP_ID,
 };
 const required = [
  ["VITE_FIREBASE_API_KEY", config.apiKey],
  ["VITE_FIREBASE_AUTH_DOMAIN", config.authDomain],
  ["VITE_FIREBASE_PROJECT_ID", config.projectId],
  ["VITE_FIREBASE_APP_ID", config.appId],
 ];
 const missing = required.filter(([, value]) => !value).map(([name]) => name);
 if (missing.length > 0) {
  throw new Error(`Firebase setup incomplete. Add ${missing.join(", ")} to .env.local and restart Vite.`);
 }
 return config;
}

/** Initialise only when a Firebase feature is used; safe under Vite hot reload. */
export function getFirebaseApp(): FirebaseApp {
 return getApps().length ? getApp() : initializeApp(configFromEnvironment());
}

/** Future Firebase login code should obtain the Auth instance from here. */
export function getFirebaseAuth(): Auth {
 return getAuth(getFirebaseApp());
}
