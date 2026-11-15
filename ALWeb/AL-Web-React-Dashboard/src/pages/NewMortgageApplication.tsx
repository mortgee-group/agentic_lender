import {useState,type FormEvent} from "react";
import {createCoreApplication,type CoreApplicationCreate} from "../services/api";

type NumericField = "householdAnnualGrossIncome"|"existingDebtLiabilities"|"deposit"|"requestedMortgageTerm"|"sellerAskingPrice"|"floor_area"|"bedrooms"|"bathrooms"|"year_built";
type FormData = Record<NumericField,string> & {county:string;property_type:string;ber_rating:string};
const initial:FormData={
 householdAnnualGrossIncome:"",existingDebtLiabilities:"",deposit:"",requestedMortgageTerm:"",sellerAskingPrice:"",
 county:"",floor_area:"",bedrooms:"",bathrooms:"",year_built:"",property_type:"",ber_rating:"",
};
const propertyTypes=[
 ["APARTMENT","Apartment"],["DETACHED","Detached"],["SEMI_DETACHED","Semi-detached"],["TERRACED","Terraced"],
 ["BUNGALOW","Bungalow"],["TOWN_HOUSE","Town house"],["COMMERCIAL","Commercial"],["OTHER","Other"],
] as const;
const berRatings=["A1","A2","A3","B1","B2","B3","C1","C2","C3","D1","D2","E1","E2","F","G","EXEMPT"];
export default function NewMortgageApplication({onSaved,onCancel}:{
 onSaved:(applicationId:string)=>void;onCancel:()=>void;
}){
 const [form,setForm]=useState<FormData>(initial);
 const [submitting,setSubmitting]=useState(false);
 const [error,setError]=useState("");
 const update=(key:keyof FormData,value:string)=>setForm(prev=>({...prev,[key]:value}));
 const numeric=(key:NumericField)=>Number(form[key]);
 const input=(key:NumericField,label:string,opts:{min?:number;max?:number;step?:string}={})=>
  <div className="field"><label htmlFor={key}>{label}</label><input id={key} type="number" value={form[key]} onChange={e=>update(key,e.target.value)}
   required min={opts.min??0} max={opts.max} step={opts.step??"1"} disabled={submitting}/></div>;
 const select=(key:"property_type"|"ber_rating",label:string,options:readonly (readonly [string,string])[])=>
  <div className="field"><label htmlFor={key}>{label}</label><select id={key} value={form[key]} onChange={e=>update(key,e.target.value)} required disabled={submitting}>
   <option value="">Select…</option>{options.map(([value,text])=><option key={value} value={value}>{text}</option>)}
  </select></div>;
 const requested=numeric("sellerAskingPrice")-numeric("deposit");
 const submit=async(event:FormEvent<HTMLFormElement>)=>{
  event.preventDefault();setError("");
  if(!form.county.trim()){setError("Enter a property county.");return;}
  if(numeric("deposit")>=numeric("sellerAskingPrice")){
   setError("Deposit must be less than the asking price.");return;
  }
  const payload:CoreApplicationCreate={
   householdAnnualGrossIncome:numeric("householdAnnualGrossIncome"),
   existingDebtLiabilities:numeric("existingDebtLiabilities"),
   deposit:numeric("deposit"),
   requestedMortgageTerm:numeric("requestedMortgageTerm"),
   sellerAskingPrice:numeric("sellerAskingPrice"),
   property:{
    county:form.county.trim(),
    floor_area:numeric("floor_area"),
    bedrooms:numeric("bedrooms"),
    bathrooms:numeric("bathrooms"),
    year_built:numeric("year_built"),
    property_type:form.property_type as CoreApplicationCreate["property"]["property_type"],
    ber_rating:form.ber_rating as CoreApplicationCreate["property"]["ber_rating"],
   },
  };
  setSubmitting(true);
  try{
   const response=await createCoreApplication(payload);
   onSaved(response.applicationId);
  }catch(e){setError(e instanceof Error?e.message:"Unable to submit application");}
  finally{setSubmitting(false);}
 };
 return <section className="page active new-core-application">
  <span className="eyebrow">Borrower workspace · AL-Core</span>
  <h1 className="page-title">New mortgage application</h1>
  <p className="subtitle">Submit a <strong>fictional test application</strong> to the real AL-Core API. This creates a persistent Firestore record.</p>
  <div className="notice"><strong>Development testing only.</strong> Do not enter real personal, property or financial details. Submitted records are saved in the shared Firebase project.</div>
  <form onSubmit={e=>void submit(e)} className="panel form-panel">
   <h2>Financial information</h2>
   <div className="fields">
    {input("householdAnnualGrossIncome","Household annual gross income (€)",{step:"0.01"})}
    {input("existingDebtLiabilities","Existing debt liabilities (€)",{step:"0.01"})}
    {input("sellerAskingPrice","Seller asking price (€)",{min:1,step:"0.01"})}
    {input("deposit","Deposit (€)",{step:"0.01"})}
    {input("requestedMortgageTerm","Mortgage term (years)",{min:1,max:40})}
   </div>
   <p className="core-form-calculated">Estimated requested mortgage: <strong>{requested>0&&form.sellerAskingPrice&&form.deposit?new Intl.NumberFormat("en-IE",{style:"currency",currency:"EUR",maximumFractionDigits:0}).format(requested):"—"}</strong></p>
   <h2 className="subhead">Property information</h2>
   <div className="fields">
    <div className="field"><label htmlFor="county">County</label><input id="county" value={form.county} onChange={e=>update("county",e.target.value)} required disabled={submitting} maxLength={100}/></div>
    {input("floor_area","Floor area (m²)",{min:0.01,step:"0.01"})}
    {input("bedrooms","Bedrooms",{min:0})}
    {input("bathrooms","Bathrooms",{min:0})}
    {input("year_built","Year built",{min:1800,max:2030})}
    {select("property_type","Property type",propertyTypes)}
    {select("ber_rating","BER rating",berRatings.map(x=>[x,x] as const))}
   </div>
   {error&&<div className="auth-error core-form-error" role="alert">{error}</div>}
   <div className="form-actions">
    <button type="submit" className="btn primary" disabled={submitting}>{submitting?"Submitting…":"Submit test application"}</button>
    <button type="button" className="btn secondary" onClick={onCancel} disabled={submitting}>Cancel</button>
   </div>
  </form>
 </section>;
}
