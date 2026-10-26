import { useEffect, useState } from "react";
import { checkBackendHealth } from "../services/api";

export default function BackendStatus() {
  const [status, setStatus] = useState("Checking...");

  useEffect(() => {
    checkBackendHealth()
      .then((healthy) =>
        setStatus(healthy ? "Connected" : "Unavailable")
      )
      .catch(() => setStatus("Disconnected"));
  }, []);

  return <p>AL-Core: {status}</p>;
}