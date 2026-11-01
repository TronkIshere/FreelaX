#!/usr/bin/env python3
"""Read-only: which EVM features does the Fuji C-Chain accept? Uses eth_call with a state-override stub (no deployment, no funds).
Usage: python -I evm_fork_probe.py <rpc-url>"""
import json, subprocess, sys
URL = sys.argv[1]
def rpc(m, p):
    b = json.dumps({"jsonrpc": "2.0", "id": 1, "method": m, "params": p})
    o = subprocess.run(["curl", "-s", "-m", "20", "-X", "POST", "-H", "content-type: application/json", "--data", b, URL], capture_output=True, text=True).stdout
    return json.loads(o)
A = "0x00000000000000000000000000000000000000f1"
def run(code):
    r = rpc("eth_call", [{"to": A}, "latest", {A: {"code": code}}])
    return r.get("result") or ("ERR " + json.dumps(r.get("error")))
out = {}
out["PUSH0 (Shanghai)"] = run("0x5f5f5260205ff3")
out["TSTORE/TLOAD (Cancun, EIP-1153)"] = run("0x600160005d60005c60005260206000f3")
out["MCOPY (Cancun, EIP-5656)"] = run("0x6001600052" + "602060006020" + "5e" + "60206020f3")
out["BLOBBASEFEE (Cancun, 0x4a)"] = run("0x4a60005260206000f3")
out["BLOBHASH (Cancun, 0x49)"] = run("0x600049" + "60005260206000f3")
out["CLZ opcode 0x1e (Osaka)"] = run("0x6001" + "1e" + "60005260206000f3")
bls = rpc("eth_call", [{"to": "0x000000000000000000000000000000000000000b", "data": "0x" + "00" * 256}, "latest"])
out["EIP-2537 BLS12-381 G1ADD precompile 0x0b (Pectra), 256 zero bytes"] = "EMPTY (no precompile): " + repr(bls.get("result")) if bls.get("result") in ("0x", None) else bls.get("result")[:40]
out["EIP-2935 history storage contract code (Pectra)"] = "present" if len(rpc("eth_getCode", ["0x0000F90827F1C53a10cb7A02335B175320002935", "latest"])["result"]) > 2 else "absent"
out["EIP-4788 beacon roots contract code (Cancun)"] = "present" if len(rpc("eth_getCode", ["0x000F3df6D732807Ef1319fB7B8bB8522d0Beac02", "latest"])["result"]) > 2 else "absent"
print(json.dumps(out, indent=1))
