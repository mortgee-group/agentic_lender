export default function About(){return <section className="page active">
 <span className="eyebrow">Project information</span>
 <h1 className="page-title">About Agentic Lender</h1>
 <p className="subtitle">React portal for borrowers, brokers and underwriters in a third-year software development project.</p>
 <div className="about-grid">
  <div className="panel pad"><h2>AL-Web responsibilities</h2>
   <p>Firebase email/password authentication, the user portal, mortgage application submission, application review, and eventual AI workflow integration with AL-Core.</p>
  </div>
  <div className="panel pad"><h2>Technology</h2>
   {([["Frontend","React + TypeScript + Vite"],["Backend API","AL-Core / FastAPI (Cloud Run)"],["Authentication","Firebase Authentication + verified ID tokens"],["Application data","AL-Core / Firestore"],["AI Audit","Separate fictional demonstration view"]] as const).map(([name,value])=>
    <div className="detail-row" key={name}><span>{name}</span><strong>{value}</strong></div>)}
  </div>
 </div>
 <div className="panel pad section"><h2>Current integration status</h2>
  <p>The dashboards, application lists, mortgage submission and individual application details use AL-Core and Firestore. All application permissions are enforced by AL-Core; frontend role navigation is only presentation.</p>
  <p>The AI Audit page retains clearly labelled synthetic demonstrations until its real AI endpoints are integrated. Stress testing, settlement and the final secure role-provisioning workflow are still under development.</p>
 </div>
 </section>;}
