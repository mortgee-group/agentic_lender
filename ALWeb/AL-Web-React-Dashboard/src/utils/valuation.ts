import type { ValuationRequest } from "../types/valuationRequest";
import type { PropertyRecord } from "../types/property";
export const euro = (n: number) => new Intl.NumberFormat("en-IE", {style:"currency", currency:"EUR", maximumFractionDigits:0}).format(n);
export function range(value: number): [number, number] { return [Math.round(value * .9 / 1000) * 1000, Math.round(value * 1.1 / 1000) * 1000]; }
// Illustrative arithmetic from original HTML prototype; NOT a predictive model or appraisal.
export function makeDemoValue(p: ValuationRequest): number {
 const region: Record<string,number> = {Dublin:115000,Kildare:65000,Wicklow:70000,Cork:50000,Galway:45000,Other:30000};
 const multipliers: Record<string,number> = {"Detached house":1.23,"Semi-detached house":1.1,"Terraced house":.97,Apartment:.9,Other:1};
 const condition: Record<string,number> = {Excellent:1.1,Good:1,Fair:.91,"Needs renovation":.79};
 return Math.round(((95000+(region[p.county]??30000)+(p.floor*1750)+(p.beds*9000)+(p.baths*6000))*(multipliers[p.type]??1)*(condition[p.condition]??1))/5000)*5000;
}
export function makeDemoRecord(p: ValuationRequest, id: number): PropertyRecord {
 return {...p, built:p.built ?? 0, energy:p.energy ?? "", value:makeDemoValue(p), id, date:new Date().toLocaleDateString("en-IE",{day:"numeric",month:"short",year:"numeric"}), demo:false};
}
