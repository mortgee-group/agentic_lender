// Local demo login server. NEVER deploy plaintext-password authentication.
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { randomBytes, timingSafeEqual } from "node:crypto";

const port = Number(process.env.AUTH_PORT || 3001);
const usersFile = join(dirname(fileURLToPath(import.meta.url)), "users.txt");
const sessions = new Map(); // token -> username, kept in memory only
const attempts = new Map(); // basic IP-based throttling, for local use only
const COOKIE_NAME = "alweb_demo_session";
const cookieOptions = "HttpOnly; SameSite=Lax; Path=/; Max-Age=28800";
const json = (res,status,data,headers={})=>{res.writeHead(status,{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store",...headers});res.end(JSON.stringify(data));};
const getToken = req => (req.headers.cookie||"").split(";").map(v=>v.trim()).find(v=>v.startsWith(COOKIE_NAME+"="))?.slice(COOKIE_NAME.length+1);
async function loadUsers() {
 const content = await readFile(usersFile,"utf8");
 return content.split(/\r?\n/).map(v=>v.trim()).filter(v=>v && !v.startsWith("#")).map(line=>{
   const i=line.indexOf(":");if(i<1)throw new Error("Malformed entry in server/users.txt");
   return {username:line.slice(0,i).trim(),password:line.slice(i+1)};
 });
}
function equal(a,b){const left=Buffer.from(a),right=Buffer.from(b);return left.length===right.length && timingSafeEqual(left,right);}
async function bodyJson(req){let text="";for await(const chunk of req){text+=chunk;if(text.length>8192)throw new Error("Request too large");}return JSON.parse(text);}
createServer(async(req,res)=>{
 const url=new URL(req.url||"/",`http://${req.headers.host||"localhost"}`);
 if(url.pathname==="/api/me" && req.method==="GET"){
   const user=sessions.get(getToken(req));return user?json(res,200,{user:{username:user}}):json(res,401,{error:"Not signed in"});
 }
 if(url.pathname==="/api/logout" && req.method==="POST"){
   sessions.delete(getToken(req));return json(res,200,{ok:true},{"Set-Cookie":`${COOKIE_NAME}=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0`});
 }
 if(url.pathname==="/api/login" && req.method==="POST"){
   const ip=req.socket.remoteAddress||"local";const entry=attempts.get(ip)||{count:0,since:Date.now()};
   if(Date.now()-entry.since>60000){entry.count=0;entry.since=Date.now();}
   if(entry.count>=10)return json(res,429,{error:"Too many attempts. Please try again in one minute."});
   try {
     const data=await bodyJson(req);
     if(typeof data.username!=="string"||typeof data.password!=="string")return json(res,400,{error:"Username and password are required"});
     const users=await loadUsers();const matched=users.find(u=>equal(u.username,data.username.trim())&&equal(u.password,data.password));
     if(!matched){entry.count++;attempts.set(ip,entry);return json(res,401,{error:"Incorrect username or password"});}
     attempts.delete(ip);
     const token=randomBytes(32).toString("hex");sessions.set(token,matched.username);
     return json(res,200,{user:{username:matched.username}},{"Set-Cookie":`${COOKIE_NAME}=${token}; ${cookieOptions}`});
   }catch(err){return json(res,400,{error:"Unable to process login request"});}
 }
 return json(res,404,{error:"Not found"});
}).listen(port,"127.0.0.1",()=>console.log(`AL-Web demo auth server listening on http://127.0.0.1:${port}`));
