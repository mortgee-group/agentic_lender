/** Demo portal roles; production roles will come from verified Firebase/AL-Core claims. */
export type PortalRole = "broker" | "underwriter";
export const roleLabel = (role: PortalRole) => role === "broker" ? "Mortgage Broker" : "Underwriting Officer";
