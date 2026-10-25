import type { PropertyRecord } from "../types/property";

// Identical fictional sample records from the original AL-Web HTML prototype.
export const sampleValuations: PropertyRecord[] = [
  { id: 1, address: "12 Example Street, Dublin", county: "Dublin", type: "Semi-detached house", beds: 3, baths: 2, floor: 110, built: 2008, condition: "Good", energy: "B", value: 420000, date: "8 Oct 2026", demo: true },
  { id: 2, address: "8 Sample Avenue, Naas", county: "Kildare", type: "Detached house", beds: 4, baths: 3, floor: 160, built: 2001, condition: "Excellent", energy: "C", value: 495000, date: "6 Oct 2026", demo: true },
  { id: 3, address: "24 Fictional Quay, Cork", county: "Cork", type: "Apartment", beds: 2, baths: 1, floor: 78, built: 2015, condition: "Good", energy: "B", value: 275000, date: "2 Oct 2026", demo: true },
];
