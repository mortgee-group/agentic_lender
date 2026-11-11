import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { getFirebaseApp, getFirebaseAuth } from "./firebase/firebase";
import "./styles/global.css";
import "./styles/role-portals.css";

// Temporary local-only Firebase SDK initialization check.
// This does not sign in a user or verify a connection to the Firebase service.
if (import.meta.env.DEV) {
 try {
  const firebaseApp = getFirebaseApp();
  const auth = getFirebaseAuth();
  console.log("Firebase initialised successfully");
  console.log("Firebase Project ID:", firebaseApp.options.projectId);
  console.log("Firebase Auth initialised:", Boolean(auth));
 } catch (error) {
  console.error("Firebase initialisation failed:", error);
 }
}

ReactDOM.createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
