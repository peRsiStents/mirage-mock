"""
验证「TCP 用例一键配置帧格式」生成的 tcp_config 是否真的可用。

做法：
  1. 完全复刻前端 buildTcpConfig() 的逻辑，生成 4 种帧类型的 tcp_config；
  2. 按同样的帧配置各起一个 TCP 监听器 + 接口 + 规则；
  3. 用生成的 tcp_config 建 TCP 测试用例并运行，断言通过且响应体正确。
"""
import json
import urllib.request

BASE = "http://127.0.0.1:9080/api/v1"


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r, timeout=30) as resp:
        return json.loads(resp.read().decode())


# ---------- 1. 复刻前端 buildTcpConfig() ----------
def default_tcp_frame():
    return {
        "type": "length_field", "lenBytes": 4, "offset": 0, "adjustment": 0,
        "initialStrip": 4, "prefixHex": "", "delimiter": "\\n", "fixedLength": 64,
    }


def build_frame_json(f):
    if f["type"] == "delimiter":
        return {"type": "delimiter", "value": f["delimiter"] or "\\n"}
    if f["type"] == "fixed":
        return {"type": "fixed", "length": int(f["fixedLength"]) or 64}
    if f["type"] == "close_end":
        return {"type": "close_end"}
    offset = int(f["offset"]) or 0
    len_bytes = int(f["lenBytes"]) or 4
    o = {
        "type": "length_field", "lenBytes": len_bytes, "offset": offset,
        "adjustment": int(f["adjustment"]) or 0,
        "initialStrip": offset + len_bytes if f["initialStrip"] is None else int(f["initialStrip"]),
    }
    if f.get("prefixHex") and str(f["prefixHex"]).strip():
        o["prefix_hex"] = str(f["prefixHex"]).strip()
    return o


def build_tcp_config(frame, fmt="json"):
    fc = build_frame_json(frame)
    format_config = None
    if fmt == "key_value":
        format_config = {"pairSep": "&", "kvSep": "="}
    return {"frameConfig": fc, "messageFormat": fmt, "formatConfig": format_config}


# ---------- 主流程 ----------
tok = req("POST", "/auth/login", {"username": "admin", "password": "admin123"})["data"]["token"]
pid = req("GET", "/projects", token=tok)["data"][0]["id"]

CASES = [
    ("length_field", dict(default_tcp_frame()), "json"),
    ("delimiter", dict(default_tcp_frame(), type="delimiter", delimiter="\\n"), "json"),
    # 定长必须 >= 响应报文长度，否则服务端按定长截断（响应变成非法 JSON）
    ("fixed", dict(default_tcp_frame(), type="fixed", fixedLength=128), "json"),
    ("close_end", dict(default_tcp_frame(), type="close_end"), "json"),
]

created = []
ok_all = True
for idx, (label, frame, fmt) in enumerate(CASES):
    port = 9101 + idx
    tcp_cfg = build_tcp_config(frame, fmt)

    lis = req("POST", "/projects/%s/listeners" % pid, {
        "projectId": int(pid), "name": "vf-" + label, "port": port,
        "connMode": "LONG", "matchMode": "ASYNC",
        "frameConfig": json.dumps(tcp_cfg["frameConfig"]),
        "messageFormat": fmt, "messageFormatConfig": None,
        "routeExtract": "$.transCode", "serialExtract": "$.serialNo", "status": 1,
    }, tok)["data"]
    req("POST", "/listeners/%s/start" % lis["id"], None, tok)

    iface = req("POST", "/projects/%s/interfaces" % pid, {
        "projectId": int(pid), "name": "vf-iface-" + label, "protocol": "TCP",
        "tcpListenerId": lis["id"], "tcpRouteExpr": "0200", "status": 1,
    }, tok)["data"]
    req("POST", "/interfaces/%s/rules" % iface["id"], {
        "name": "vf-rule", "priority": 10, "matchCondition": [],
        "responseTemplate": {
            "transCode": "0210", "serialNo": "${field.serialNo}",
            "respCode": "00", "amount": "${field.amount}",
        },
        "delayType": "NONE", "faultType": "NONE", "status": 1,
    }, tok)

    tc = req("POST", "/projects/%s/testcases" % pid, {
        "projectId": int(pid), "name": "vf-case-" + label, "protocol": "TCP",
        "url": "127.0.0.1:%d" % port, "mode": "proxy", "status": 1,
        "body": json.dumps({"transCode": "0200", "serialNo": "SN-%s" % label, "amount": 100}),
        "tcpConfig": json.dumps(tcp_cfg, indent=2),
        "assertions": json.dumps([{"type": "jsonPath", "target": "$.respCode", "op": "eq", "expected": "00"}]),
    }, tok)["data"]

    res = req("POST", "/testcases/%s/run" % tc["id"], None, tok)["data"]
    body = json.loads(res.get("body") or "{}")
    passed = bool(res.get("passed"))
    err = res.get("error")
    echo_ok = body.get("serialNo") == "SN-%s" % label
    status = "PASS" if (passed and echo_ok) else "FAIL"
    if status == "FAIL":
        ok_all = False
    print("[%s] %-13s cost=%-5s serialNo=%-18s respCode=%s error=%s"
          % (status, label, res.get("costMs"), body.get("serialNo"), body.get("respCode"), err))
    if err:
        print("      raw body: %r" % res.get("body"))
    created.append((lis["id"], iface["id"], tc["id"]))

for lis_id, iface_id, tc_id in created:
    for path in ("/listeners/%s" % lis_id, "/interfaces/%s" % iface_id, "/testcases/%s" % tc_id):
        try:
            req("DELETE", path, None, tok)
        except Exception as e:
            print("cleanup warn:", path, e)

print("\nALL:", "PASS" if ok_all else "FAIL")
