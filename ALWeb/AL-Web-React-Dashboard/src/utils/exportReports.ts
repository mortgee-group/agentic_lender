import type {CoreApplication} from "../services/api";

/** Export only records returned by AL-Core's authenticated endpoints.
 * Missing audit evidence is stated honestly. This code never changes a loan record.
 */
type DecisionFields={underwriterDecision?:"APPROVED"|"REJECTED"|null;decidedBy?:string|null;decidedAt?:string|null;DTI?:number|null};
type ExportApplication=CoreApplication&DecisionFields;
const empty=(v:unknown)=>v===null||v===undefined||v===""?"Not available":String(v);
const money=(v:number|null|undefined)=>typeof v==="number"&&Number.isFinite(v)?"EUR "+new Intl.NumberFormat("en-IE",{minimumFractionDigits:2,maximumFractionDigits:2}).format(v):"Not available";
const percent=(v:number|null|undefined)=>typeof v==="number"&&Number.isFinite(v)?v.toFixed(2)+"%":"Not available";
const date=(v:string|null|undefined)=>v?new Date(v).toLocaleString("en-IE"):"Not available";
const decision=(a:ExportApplication)=>a.underwriterDecision??"Not recorded (not an approval)";
const safelyQuotedCsvCell=(v:unknown):string=>{
 const raw=v==null?"":String(v);
 // Protect CSV/Excel against formula injection, including leading whitespace.
 const safe=/^[\s]*[=+@\-]/.test(raw)?`'${raw}`:raw;
 return '"'+safe.replace(/"/g,'""')+'"';
};
export function makePortfolioCsv(applications:CoreApplication[]):string{
 const header=["Application ID","Lifecycle status","Human decision","Decided by UID","Decision timestamp",
  "Borrower UID","Broker UID","County","Property type","Asking price EUR","Deposit EUR",
  "Requested loan EUR","Mortgage term years","Gross annual income EUR","Existing liabilities EUR",
  "Estimated LTV percent","Risk tier","Approval likelihood","Submitted at"];
 const body=applications.map(original=>{
  const a=original as ExportApplication;
  return [a.applicationId,a.lifecycleStatus,a.underwriterDecision??"",a.decidedBy??"",a.decidedAt??"",
   a.borrowerId,a.brokerId??"",a.property?.county??"",a.property?.property_type??"",
   a.sellerAskingPrice,a.deposit,a.requestedLoanAmount??"",a.requestedMortgageTerm,
   a.householdAnnualGrossIncome,a.existingDebtLiabilities,a.estimatedLTV??"",
   a.riskTier??"",a.approvalLikelihood??"",a.submittedAt??""].map(safelyQuotedCsvCell).join(",");
 });
 return "\uFEFF"+[header.map(safelyQuotedCsvCell).join(","),...body].join("\r\n")+"\r\n";
}
export function reportLines(original:CoreApplication):string[]{
 const a=original as ExportApplication;
 return [
  "AGENTIC LENDER - MORTGAGE APPLICATION REPORT",
  "Read-only test export. Generated: "+new Date().toLocaleString("en-IE"),
  "Source: authenticated AL-Core application record (no AI audit read endpoint).",
  "",
  "APPLICATION",
  "Application ID: "+empty(a.applicationId),
  "Lifecycle status: "+empty(a.lifecycleStatus),
  "Submitted: "+date(a.submittedAt),
  "Last updated: "+date(a.updatedAt),
  "Borrower UID: "+empty(a.borrowerId),
  "Assigned broker UID: "+empty(a.brokerId),
  "",
  "FINANCIAL DETAILS",
  "Property asking price: "+money(a.sellerAskingPrice),
  "Deposit: "+money(a.deposit),
  "Requested mortgage: "+money(a.requestedLoanAmount),
  "Requested mortgage term: "+empty(a.requestedMortgageTerm)+" years",
  "Gross household annual income: "+money(a.householdAnnualGrossIncome),
  "Existing debt liabilities: "+money(a.existingDebtLiabilities),
  "Estimated LTV: "+percent(a.estimatedLTV),
  "Backend DTI (if returned): "+percent(a.DTI),
  "",
  "PROPERTY",
  "County: "+empty(a.property?.county),
  "Property type: "+empty(a.property?.property_type),
  "Floor area: "+empty(a.property?.floor_area)+" sqm",
  "Bedrooms: "+empty(a.property?.bedrooms),
  "Bathrooms: "+empty(a.property?.bathrooms),
  "Year built: "+empty(a.property?.year_built),
  "BER rating: "+empty(a.property?.ber_rating),
  "",
  "AI-RELATED SUMMARY (APPLICATION FIELDS ONLY)",
  "Risk tier: "+empty(a.riskTier),
  "Approval likelihood: "+empty(a.approvalLikelihood),
  "ML valuation amount: Not available in the application API response.",
  "Full credit memorandum: Not available via current AL-Core read API.",
  "Individual AI agent reasoning and consensus: Not available via current AL-Core read API.",
  "",
  "FINAL HUMAN UNDERWRITER DECISION",
  "Decision: "+decision(a),
  "Decided by UID: "+empty(a.decidedBy),
  "Decided at: "+date(a.decidedAt),
  "Important: lifecycle CLOSED does not itself mean approved or rejected.",
  "Human approval is recorded only if underwriterDecision is APPROVED.",
  "",
  "AUDIT AND SETTLEMENT LIMITATIONS",
  "This report is NOT a verified audit pack or an executed loan agreement.",
  "Blockchain transaction/hash and token ID: Not available via current AL-Core read API.",
  "No on-chain settlement or digital signing was performed by this export.",
  "All fields reflect the API response at export time; missing information is not invented.",
 ];
}
// Portable dependency-free PDF writer (PDF 1.4, standard Helvetica).
// It intentionally supports plain ASCII PDF text. Non-ASCII is transliterated or replaced.
const ascii=(s:string)=>s.normalize("NFKD").replace(/[\u0300-\u036f]/g,"").replace(/[^\x20-\x7E]/g,"?");
const pdfEscape=(s:string)=>ascii(s).replace(/\\/g,"\\\\").replace(/\(/g,"\\(").replace(/\)/g,"\\)");
function wrapText(s:string,max=94):string[]{
 if(s.length<=max)return [s];
 const result:string[]=[];let chunk=s;
 while(chunk.length>max){
  let at=chunk.lastIndexOf(" ",max);
  if(at<1)at=max;
  result.push(chunk.slice(0,at));
  chunk=chunk.slice(at).trimStart();
 }
 result.push(chunk);
 return result;
}
export function makeReportPdf(record:CoreApplication):Uint8Array{
 const lines=reportLines(record).flatMap(line=>wrapText(ascii(line)));
 const perPage=51;
 const groups:string[][]=[];
 for(let i=0;i<lines.length;i+=perPage)groups.push(lines.slice(i,i+perPage));
 const objects:string[]=[
  "<< /Type /Catalog /Pages 2 0 R >>",
  "", // parent Pages object filled after page objects are known
  "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
 ];
 const pageIds:number[]=[];
 for(let page=0;page<groups.length;page++){
  const commands:string[]=[];
  for(let line=0;line<groups[page].length;line++){
   const label=groups[page][line];
   const bold=label==="AGENTIC LENDER - MORTGAGE APPLICATION REPORT"||
    ["APPLICATION","FINANCIAL DETAILS","PROPERTY","AI-RELATED SUMMARY (APPLICATION FIELDS ONLY)",
     "FINAL HUMAN UNDERWRITER DECISION","AUDIT AND SETTLEMENT LIMITATIONS"].includes(label);
   const y=770-line*14;
   commands.push(`BT /${bold?"F2":"F1"} ${bold?11:9} Tf 45 ${y} Td (${pdfEscape(label)}) Tj ET`);
  }
  commands.push(`BT /F1 8 Tf 45 40 Td (Page ${page+1} of ${groups.length}) Tj ET`);
  const stream=commands.join("\n")+"\n";
  const contentId=objects.length+1;
  objects.push(`<< /Length ${stream.length} >>\nstream\n${stream}endstream`);
  const pageId=objects.length+1;
  objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentId} 0 R >>`);
  pageIds.push(pageId);
 }
 objects[1]=`<< /Type /Pages /Count ${pageIds.length} /Kids [${pageIds.map(id=>id+" 0 R").join(" ")}] >>`;
 let pdf="%PDF-1.4\n";
 const offsets=[0];
 for(let i=0;i<objects.length;i++){
  offsets.push(pdf.length);
  pdf+=`${i+1} 0 obj\n${objects[i]}\nendobj\n`;
 }
 const xrefOffset=pdf.length;
 pdf+=`xref\n0 ${objects.length+1}\n0000000000 65535 f \n`;
 for(let i=1;i<offsets.length;i++)pdf+=String(offsets[i]).padStart(10,"0")+" 00000 n \n";
 pdf+=`trailer\n<< /Size ${objects.length+1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
 return new TextEncoder().encode(pdf);
}
export function downloadBrowserFile(data:BlobPart,mime:string,filename:string):void{
 const blob=new Blob([data],{type:mime});
 const url=URL.createObjectURL(blob);
 const anchor=document.createElement("a");anchor.href=url;anchor.download=filename;
 document.body.appendChild(anchor);anchor.click();anchor.remove();
 window.setTimeout(()=>URL.revokeObjectURL(url),60000);
}
export const safeFilename=(id:string)=>id.replace(/[^a-zA-Z0-9_-]/g,"_").slice(0,80);
