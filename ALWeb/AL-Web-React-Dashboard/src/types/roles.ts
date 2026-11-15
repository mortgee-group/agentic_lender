/** Demo portal roles; production roles will come from verified Firebase/AL-Core claims. */
export type PortalRole = "borrower" | "broker" | "underwriter";
export const roleLabel = (role: PortalRole) =>
 role === "borrower" ? "Borrower" : role === "broker" ? "Mortgage Broker" : "Underwriting Officer";
