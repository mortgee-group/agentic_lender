import { useState, type FormEvent } from "react";
import type { QuickProperty } from "./Dashboard";
import type { ValuationRequest } from "../types/valuationRequest";

type Props = {
  initialValues?: QuickProperty | ValuationRequest | null;
  onSubmit: (request: ValuationRequest) => void;
};

type FormValues = {
  address: string;
  county: string;
  type: string;
  beds: string;
  baths: string;
  floor: string;
  built: string;
  condition: string;
  energy: string;
};

const counties = ["Dublin", "Kildare", "Wicklow", "Cork", "Galway", "Other"];
const propertyTypes = ["Detached house", "Semi-detached house", "Terraced house", "Apartment", "Other"];
const conditions = ["Good", "Excellent", "Fair", "Needs renovation"];
const berRatings = ["A", "B", "C", "D", "E", "F", "G"];

function blankForm(quick?: QuickProperty | ValuationRequest | null): FormValues {
  const full = quick && "county" in quick ? quick : null;
  return {
    address: quick?.address ?? "",
    county: full?.county ?? "",
    type: quick?.type ?? "",
    beds: full ? String(full.beds) : "",
    baths: full ? String(full.baths) : "",
    floor: full ? String(full.floor) : "",
    built: full?.built === undefined ? "" : String(full.built),
    condition: full?.condition ?? "Good",
    energy: full?.energy ?? "",
  };
}

export default function NewValuation({ initialValues, onSubmit }: Props) {
  const [values, setValues] = useState<FormValues>(() => blankForm(initialValues));
  const [error, setError] = useState("");

  function update<K extends keyof FormValues>(field: K, value: FormValues[K]) {
    setValues((previous) => ({ ...previous, [field]: value }));
    if (error) setError("");
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const address = values.address.trim();
    const beds = Number(values.beds);
    const baths = Number(values.baths);
    const floor = Number(values.floor);
    const built = values.built === "" ? undefined : Number(values.built);

    if (!address || !values.county || !values.type || values.beds === "" || values.baths === "" || values.floor === "") {
      setError("Please complete all required fields.");
      return;
    }
    if (!Number.isInteger(beds) || beds < 0 || beds > 25 || !Number.isInteger(baths) || baths < 0 || baths > 25 || !Number.isFinite(floor) || floor < 10 || floor > 5000) {
      setError("Check the bedroom, bathroom and floor-area values.");
      return;
    }
    if (built !== undefined && (!Number.isInteger(built) || built < 1700 || built > 2026)) {
      setError("Year built must be between 1700 and 2026.");
      return;
    }

    const request: ValuationRequest = {
      address, county: values.county, type: values.type,
      beds, baths, floor, ...(built === undefined ? {} : { built }),
      condition: values.condition,
      ...(values.energy ? { energy: values.energy } : {}),
    };
    onSubmit(request);
  }

  function resetForm() {
    setValues(blankForm());
    setError("");
  }

  return <section className="page active" id="page-new">
    <span className="eyebrow">New valuation</span>
    <h1 className="page-title">Property Details</h1>
    <p className="subtitle">Enter property information to prepare a simulated valuation request.</p>
    <div className="steps" aria-label="Valuation progress">
      <span className="step active"><span className="bubble">1</span>Property details</span>
      <span className="step-sep" />
      <span className="step"><span className="bubble">2</span>Valuation result</span>
    </div>
    <div className="form-layout">
      <div className="panel form-panel">
        <h2>Property Information</h2>
        <form onSubmit={handleSubmit} onReset={resetForm}>
          <div className="fields">
            <div className="field full"><label htmlFor="address">Property address *</label><input id="address" name="address" placeholder="e.g. 12 Example Street, Dublin" required value={values.address} onChange={e => update("address", e.target.value)} /></div>
            <div className="field"><label htmlFor="county">County / Region *</label><select id="county" required value={values.county} onChange={e => update("county", e.target.value)}><option value="">Choose county</option>{counties.map(c => <option key={c} value={c}>{c}</option>)}</select></div>
            <div className="field"><label htmlFor="type">Property type *</label><select id="type" required value={values.type} onChange={e => update("type", e.target.value)}><option value="">Choose type</option>{propertyTypes.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
            <div className="field"><label htmlFor="beds">Bedrooms *</label><input id="beds" type="number" min="0" max="25" step="1" placeholder="3" required value={values.beds} onChange={e => update("beds", e.target.value)} /></div>
            <div className="field"><label htmlFor="baths">Bathrooms *</label><input id="baths" type="number" min="0" max="25" step="1" placeholder="2" required value={values.baths} onChange={e => update("baths", e.target.value)} /></div>
            <div className="field"><label htmlFor="floor">Floor area (m²) *</label><input id="floor" type="number" min="10" max="5000" step="any" placeholder="110" required value={values.floor} onChange={e => update("floor", e.target.value)} /></div>
            <div className="field"><label htmlFor="built">Year built</label><input id="built" type="number" min="1700" max="2026" step="1" placeholder="2005" value={values.built} onChange={e => update("built", e.target.value)} /></div>
          </div>
          <div className="subhead">Additional Features</div>
          <div className="fields">
            <div className="field"><label htmlFor="condition">Property condition</label><select id="condition" value={values.condition} onChange={e => update("condition", e.target.value)}>{conditions.map(c => <option key={c} value={c}>{c}</option>)}</select></div>
            <div className="field"><label htmlFor="energy">BER rating</label><select id="energy" value={values.energy} onChange={e => update("energy", e.target.value)}><option value="">Not specified</option>{berRatings.map(r => <option key={r} value={r}>{r}</option>)}</select></div>
          </div>
          {error && <div className="notice" role="alert">{error}</div>}
          <div className="form-actions"><button className="btn primary" type="submit">Generate Demo Valuation →</button><button className="btn secondary" type="reset">Clear Form</button></div>
          <div className="notice">Prototype only: no request is sent to the backend and no valuation is calculated yet. The next screen uses a synthetic arithmetic formula to display an example price.</div>
        </form>
      </div>
      <div className="panel pad">
        <div className="help-box"><strong>How it works</strong><p>1. Enter property details.<br/>2. Submit the form.<br/>3. Review the prepared request.<br/>4. Connect valuation results when AL-Core is available.</p></div>
        <h2 style={{ marginTop: 24 }}>Future integration</h2>
        <p style={{ fontSize: 12 }}>In the final architecture, React will submit a validated request to AL-Core over REST. AL-Core will coordinate the valuation model and any AI-generated explanation.</p>
        <div className="detail-row"><span>Frontend</span><strong>React + TypeScript</strong></div>
        <div className="detail-row"><span>Communication</span><strong>REST / JSON</strong></div>
        <div className="detail-row"><span>Backend</span><strong>AL-Core</strong></div>
        <div className="detail-row"><span>Model</span><strong>AL-AI</strong></div>
      </div>
    </div>
  </section>;
}
