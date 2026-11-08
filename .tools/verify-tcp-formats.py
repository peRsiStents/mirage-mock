"""
验证「一键配置帧格式」里的报文格式选择（json / key_value / fixed_fields / hex_string）。

做法与 verify-tcp-frame-config.py 相同：按 UI 生成的 formatConfig 建监听器 + 接口 + 规则，
再用 TCP 用例跑一遍，断言响应字段正确。
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


# 帧固定用长度头（UI 默认），这里只验证报文格式
FRAME = {"type": "length_field", "lenBytes": 4, "offset": 0, "adjustment": 0, "initialStrip": 4}

CASES = [
    {
        "label": "json",
        "format": "json",
        "formatConfig": None,
        "body": {"transCode": "0200", "serialNo": "SN-json"},
        "route": "$.transCode",
        "routeValue": "0200",
        "template": {"transCode": "0210", "serialNo": "${field.serialNo}", "respCode": "00"},
        "assert": {"target": "$.respCode", "expected": "00"},
        "echo": ("serialNo", "SN-json"),
    },
    {
        "label": "key_value",
        "format": "key_value",
        "formatConfig": {"pairSep": "&", "kvSep": "="},
        "body": {"transCode": "0200", "serialNo": "SN-kv"},
        "route": "$.transCode",
        "routeValue": "0200",
        "template": {"transCode": "0210", "serialNo": "${field.serialNo}", "respCode": "00"},
        "assert": {"target": "$.respCode", "expected": "00"},
        "echo": ("serialNo", "SN-kv"),
    },
    {
        "label": "fixed_fields",
        "format": "fixed_fields",
        "formatConfig": {"fields": [{"name": "transCode", "len": 4}, {"name": "serialNo", "len": 8}]},
        "body": {"transCode": "0200", "serialNo": "SN-ff"},
        "route": "$.transCode",
        "routeValue": "0200",
        "template": {"transCode": "0210", "serialNo": "${field.serialNo}", "respCode": "00"},
        # 定长字段只在线路上传 formatConfig 里列出的字段，respCode 不在其中，只能断言 serialNo
        "assert": {"target": "$.serialNo", "expected": "SN-ff"},
        "echo": ("serialNo", "SN-ff"),
    },
    {
        "label": "hex_string",
        "format": "hex_string",
        "formatConfig": None,
        "body": {"_hex": "30323030534e2d686578"},
        "route": "$._hex",
        # 路由值按小写十六进制比较（Codec.hex 输出小写）
        "routeValue": "30323030534e2d686578",
        "template": {"_hex": "${field._hex}"},
        "assert": {"target": "$._hex", "expected": "30323030534e2d686578"},
        "echo": ("_hex", "30323030534e2d686578"),
    },
]

tok = req("POST", "/auth/login", {"username": "admin", "password": "admin123"})["data"]["token"]
pid = req("GET", "/projects", token=tok)["data"][0]["id"]

created = []
ok_all = True
for idx, c in enumerate(CASES):
    port = 9301 + idx
    tcp_cfg = {"frameConfig": FRAME, "messageFormat": c["format"], "formatConfig": c["formatConfig"]}

    lis = req("POST", "/projects/%s/listeners" % pid, {
        "projectId": int(pid), "name": "vf-fmt-" + c["label"], "port": port,
        "connMode": "LONG", "matchMode": "ASYNC",
        "frameConfig": json.dumps(FRAME),
        "messageFormat": c["format"],
        "messageFormatConfig": None if c["formatConfig"] is None else json.dumps(c["formatConfig"]),
        "routeExtract": c["route"], "serialExtract": "$.serialNo", "status": 1,
    }, tok)["data"]
    req("POST", "/listeners/%s/start" % lis["id"], None, tok)

    iface = req("POST", "/projects/%s/interfaces" % pid, {
        "projectId": int(pid), "name": "vf-fmt-iface-" + c["label"], "protocol": "TCP",
        "tcpListenerId": lis["id"], "tcpRouteExpr": c["routeValue"], "status": 1,
    }, tok)["data"]
    req("POST", "/interfaces/%s/rules" % iface["id"], {
        "name": "vf-fmt-rule", "priority": 10, "matchCondition": [],
        "responseTemplate": c["template"],
        "delayType": "NONE", "faultType": "NONE", "status": 1,
    }, tok)

    tc = req("POST", "/projects/%s/testcases" % pid, {
        "projectId": int(pid), "name": "vf-fmt-case-" + c["label"], "protocol": "TCP",
        "url": "127.0.0.1:%d" % port, "mode": "proxy", "status": 1,
        "body": json.dumps(c["body"]),
        "tcpConfig": json.dumps(tcp_cfg, indent=2),
        "assertions": json.dumps([{"type": "jsonPath", "target": c["assert"]["target"],
                                   "op": "eq", "expected": c["assert"]["expected"]}]),
    }, tok)["data"]

    res = req("POST", "/testcases/%s/run" % tc["id"], None, tok)["data"]
    body = json.loads(res.get("body") or "{}")
    passed = bool(res.get("passed"))
    echo_ok = body.get(c["echo"][0]) == c["echo"][1]
    status = "PASS" if (passed and echo_ok) else "FAIL"
    if status == "FAIL":
        ok_all = False
    print("[%s] %-13s cost=%-5s echo=%-20s error=%s"
          % (status, c["label"], res.get("costMs"), body.get(c["echo"][0]), res.get("error")))
    if status == "FAIL":
        print("      raw body: %r" % res.get("body"))
    created.append((lis["id"], iface["id"], tc["id"]))

for lis_id, iface_id, tc_id in created:
    for path in ("/listeners/%s" % lis_id, "/interfaces/%s" % iface_id, "/testcases/%s" % tc_id):
        try:
            req("DELETE", path, None, tok)
        except Exception as e:
            print("cleanup warn:", path, e)

print("\nALL:", "PASS" if ok_all else "FAIL")
