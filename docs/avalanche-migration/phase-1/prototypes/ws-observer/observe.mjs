// Read-only Fuji observer: compares newAcceptedTransactions vs newHeads, and what HTTP RPC returns for an
// accepted tx immediately after acceptance. Node >= 22 (global WebSocket/fetch). No keys, no writes.
// usage: node observe.mjs <seconds> <ws-url> <http-url> <out.json>
const [secs, WS, HTTP, OUT] = [Number(process.argv[2]||120), process.argv[3], process.argv[4], process.argv[5]];
import { writeFileSync } from 'node:fs';
let id=100; const t0=Date.now();
const rpc=async(m,p)=>{const t=performance.now();try{const r=await fetch(HTTP,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:++id,method:m,params:p})});const j=await r.json();return {ms:Math.round(performance.now()-t),j};}catch(e){return {ms:Math.round(performance.now()-t),err:String(e)}}};
const ev={accepted:[],heads:[],probes:[],ws:[]};
const ws=new WebSocket(WS);
ws.onopen=()=>{ev.ws.push({t:Date.now()-t0,e:'open'});
 ws.send(JSON.stringify({jsonrpc:'2.0',id:1,method:'eth_subscribe',params:['newHeads']}));
 ws.send(JSON.stringify({jsonrpc:'2.0',id:2,method:'eth_subscribe',params:['newAcceptedTransactions']}));};
ws.onclose=(e)=>ev.ws.push({t:Date.now()-t0,e:'close',code:e.code});
ws.onerror=()=>ev.ws.push({t:Date.now()-t0,e:'error'});
const pend=[];
ws.onmessage=async(m)=>{const d=JSON.parse(m.data);
 if(d.id){ev.ws.push({t:Date.now()-t0,e:'subresp',id:d.id,r:d.result||d.error});return;}
 const r=d.params?.result, T=Date.now()-t0;
 if(!r)return;
 if(typeof r==='string'&&r.length===66){ // accepted tx hash
   ev.accepted.push({t:T,hash:r});
   const a=await rpc('eth_getTransactionByHash',[r]); const b=await rpc('eth_getTransactionReceipt',[r]);
   ev.probes.push({hash:r,tAccepted:T,byHash:{ms:a.ms,found:!!a.j?.result,blockNumber:a.j?.result?.blockNumber},receipt:{ms:b.ms,found:!!b.j?.result,status:b.j?.result?.status,block:b.j?.result?.blockNumber},err:a.err||b.err});
 } else if(r.number){ev.heads.push({t:T,number:parseInt(r.number,16),ts:parseInt(r.timestamp,16),txs:undefined});}
};
setTimeout(async()=>{ ws.close();
 // for the first few accepted txs: logs availability by block after the fact is covered by probes; also record tags
 const tags={};for(const t of['latest','safe','finalized','pending']){const b=await rpc('eth_getBlockByNumber',[t,false]);tags[t]=parseInt(b.j?.result?.number||'0',16)}
 ev.tags=tags;ev.seconds=secs;ev.counts={accepted:ev.accepted.length,heads:ev.heads.length};
 writeFileSync(OUT,JSON.stringify(ev,null,1));console.log(JSON.stringify({counts:ev.counts,ws:ev.ws,sample:ev.probes.slice(0,8),tags},null,1));process.exit(0)},secs*1000);
