#!/usr/bin/env python3
"""Read-only Fuji probe (stdlib only). Usage: python -I fuji_probe.py <rpc-url> <label>
Writes evidence/fuji-probe-<label>.json. No keys, no writes."""
import json, sys, time, urllib.request, datetime
URL, LABEL = sys.argv[1], sys.argv[2]
USDC = "0x5425890298aed601595a70AB815c96711a31Bc65"
USDCE = "0xA7D7079b0FEaD91F3e65f86E8915Cb59c1a4C664"  # unverified-by-official-page candidate
ZERO = "0x0000000000000000000000000000000000000001"
out = {"rpc": URL if "key" not in URL else "<redacted>", "label": LABEL, "started": datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None).isoformat()+"Z", "calls": []}
_id = 0
def rpc(method, params, timeout=20):
    global _id; _id += 1
    body = json.dumps({"jsonrpc":"2.0","id":_id,"method":method,"params":params}).encode()
    t=time.time(); status=None
    try:
        import subprocess
        cp=subprocess.run(["curl","-s","-m",str(timeout),"-w","\n%{http_code}","-X","POST","-H","content-type: application/json","-H","user-agent: freelax-phase1-probe","--data",body.decode(),URL],capture_output=True,text=True)
        txt,_,code=cp.stdout.rpartition("\n"); status=int(code) if code.isdigit() else None
        j=json.loads(txt) if txt.strip().startswith("{") else {"http_error":txt[:200] or cp.stderr[:200]}
    except Exception as e:
        j={"exception":repr(e)[:200]}
    ms=round((time.time()-t)*1000)
    out["calls"].append({"method":method,"params":str(params)[:120],"http":status,"ms":ms,"resp":str(j)[:300]})
    return j
def call(to, data, tag="latest"): return rpc("eth_call",[{"to":to,"data":data},tag]).get("result")
def s(h):
    try:
        b=bytes.fromhex(h[2:]); 
        if len(b)>=64: n=int.from_bytes(b[32:64],"big"); return b[64:64+n].decode()
        return b.rstrip(b"\0").decode()
    except Exception: return h
r={}
r["chainId"]=rpc("eth_chainId",[]).get("result")
r["net_version"]=rpc("net_version",[]).get("result")
b=rpc("eth_getBlockByNumber",["latest",False]).get("result") or {}
r["head"]=int(b.get("number","0x0"),16); r["head_ts"]=int(b.get("timestamp","0x0"),16)
r["head_keys"]=sorted(b.keys())
r["head_timestampMilliseconds"]=b.get("timestampMilliseconds")
r["wall_minus_head_ts_s"]=round(time.time()-r["head_ts"],2)
r["gasPrice"]=rpc("eth_gasPrice",[]).get("result")
r["feeHistory"]=str(rpc("eth_feeHistory",["0x4","latest",[25,75]]).get("result"))[:200]
r["baseFee_avax"]=rpc("eth_baseFee",[]).get("result")
r["suggestPriceOptions"]=str(rpc("eth_suggestPriceOptions",[]))[:200]
r["balance_zero_addr"]=rpc("eth_getBalance",[ZERO,"latest"]).get("result")
r["nonce_zero_addr"]=rpc("eth_getTransactionCount",[ZERO,"latest"]).get("result")
r["safe_block"]=int((rpc("eth_getBlockByNumber",["safe",False]).get("result") or {"number":"0x0"})["number"],16)
r["finalized_block"]=int((rpc("eth_getBlockByNumber",["finalized",False]).get("result") or {"number":"0x0"})["number"],16)
r["pending_block"]=int((rpc("eth_getBlockByNumber",["pending",False]).get("result") or {"number":"0x0"})["number"],16)
code=rpc("eth_getCode",[USDC,"latest"]).get("result") or ""
r["usdc_code_bytes"]=(len(code)-2)//2
pad=lambda a:a[2:].lower().rjust(64,"0")
r["usdc_name"]=s(call(USDC,"0x06fdde03") or "0x")
r["usdc_symbol"]=s(call(USDC,"0x95d89b41") or "0x")
r["usdc_decimals"]=int(call(USDC,"0x313ce567") or "0x0",16)
r["usdc_version"]=s(call(USDC,"0x54fd4d50") or "0x")
r["usdc_totalSupply_raw"]=int(call(USDC,"0x18160ddd") or "0x0",16)
r["usdc_balanceOf_zero"]=int(call(USDC,"0x70a08231"+pad(ZERO)) or "0x0",16)
r["usdc_allowance_zero"]=int(call(USDC,"0xdd62ed3e"+pad(ZERO)+pad(ZERO)) or "0x0",16)
r["usdc_paused"]=call(USDC,"0x5c975abb")
for nm,sel in [("DOMAIN_SEPARATOR","0x3644e515"),("PERMIT_TYPEHASH","0x30adf81f"),("nonces(0x1)","0x7ecebe00"+pad(ZERO)),
               ("TRANSFER_WITH_AUTHORIZATION_TYPEHASH","0xa0cc6a68"),("RECEIVE_WITH_AUTHORIZATION_TYPEHASH","0x7f2eecc3"),
               ("authorizationState(0x1,0x0)","0xe94a0102"+pad(ZERO)+"0"*64),("blacklister","0x5a4f5cf2"),("masterMinter","0x35d99f35")]:
    r["usdc_"+nm]=call(USDC,sel)
ce=rpc("eth_getCode",[USDCE,"latest"]).get("result") or ""
r["usdce_candidate_code_bytes"]=(len(ce)-2)//2
# EIP-1967 implementation slot
r["usdc_impl_slot"]=rpc("eth_getStorageAt",[USDC,"0x360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc","latest"]).get("result")
# logs: bounded ranges around head on USDC Transfer topic
T="0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
h=r["head"]
def logs(a,b,addr=True):
    f={"fromBlock":hex(a),"toBlock":hex(b),"topics":[T]}
    if addr: f["address"]=USDC
    j=rpc("eth_getLogs",[f],timeout=40)
    return (len(j["result"]) if "result" in j else None, str(j.get("error") or j.get("http_error") or j.get("exception") or "")[:160])
r["logs_ranges"]={}
for span in [10,100,1000,2000,5000,10000,50000,100000]:
    r["logs_ranges"][str(span)]=logs(h-span,h-1)
r["logs_no_address_100"]=logs(h-100,h-1,addr=False)
r["logs_historical_1M_back_100"]=logs(h-1_000_000,h-1_000_000+100)
# timestamps progression
ts=[]
for i in range(8):
    bb=rpc("eth_getBlockByNumber",["latest",False]).get("result") or {}
    ts.append((int(bb.get("number","0x0"),16),int(bb.get("timestamp","0x0"),16),bb.get("timestampMilliseconds"),round(time.time(),3)))
    time.sleep(1.5)
r["head_progression"]=ts
# rate limit burst
codes={}; t=time.time()
for i in range(60):
    j=rpc("eth_blockNumber",[]); c=out["calls"][-1]["http"]; codes[str(c)]=codes.get(str(c),0)+1
r["burst60_http_codes"]=codes; r["burst60_seconds"]=round(time.time()-t,2)
# unknown/missing tx and receipt
fake="0x"+"ab"*32
r["unknown_tx_by_hash"]=rpc("eth_getTransactionByHash",[fake]).get("result","<no result>")
r["unknown_receipt"]=rpc("eth_getTransactionReceipt",[fake]).get("result","<no result>")
out["summary"]=r; out["finished"]=datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None).isoformat()+"Z"
lat=sorted(c["ms"] for c in out["calls"] if c["method"]=="eth_blockNumber")
out["eth_blockNumber_latency_ms"]={"n":len(lat),"min":lat[0],"median":lat[len(lat)//2],"max":lat[-1]}
json.dump(out,open(f"evidence/fuji-probe-{LABEL}.json","w"),indent=1)
print(json.dumps(r,indent=1)[:6000]); print(out["eth_blockNumber_latency_ms"])
