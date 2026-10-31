"""Local Gateway probe using an already configured bootstrap administrator.

Never generates accounts, tokens, documents, OTPs or business fixtures.
Credentials are read only into subprocess-captured memory; responses are allowlisted.
"""
import json
import subprocess
import urllib.error
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def probe():
    result = {"status": "BLOCKED_CONFIGURED_AUTH", "steps": []}
    values = subprocess.run(
        ["docker", "exec", "duriancare-auth-service", "sh", "-c",
         'printf "%s\\000%s\\000%s" "$DEFAULT_ADMIN_ENABLED" "$DEFAULT_ADMIN_EMAIL" "$DEFAULT_ADMIN_PASSWORD"'],
        capture_output=True, check=False, timeout=15).stdout.decode().split("\x00")
    if len(values) != 3 or values[0].lower() not in {"true", "1"} or not all(values[1:]):
        return result
    opener = urllib.request.build_opener(NoRedirect())

    def call(method, path, token=None, body=None):
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request("http://localhost:8080" + path, data=data, headers=headers, method=method)
        try:
            with opener.open(req, timeout=15) as response:
                code, payload = response.status, response.read()
        except urllib.error.HTTPError as exc:
            code, payload = exc.code, exc.read()
        result["steps"].append({"method": method, "path": path, "status": code})
        try:
            return code, json.loads(payload)
        except (ValueError, UnicodeError):
            return code, {}

    status, auth = call("POST", "/api/auth/login", body={"email": values[1], "password": values[2]})
    values = []
    if status != 200 or not auth.get("accessToken"):
        message = str(auth.get("message", "")).lower()
        result["validationFields"] = [name for name in ("email", "password") if name in message]
        return result
    token = auth["accessToken"]
    status, identity = call("GET", "/api/users/me", token)
    if status != 200 or not (identity.get("id") or identity.get("userId")):
        return {**result, "status": "FAIL_IDENTITY"}
    email = identity.get("email", "")
    result["identity"] = {"userId": identity.get("id") or identity.get("userId"), "role": identity.get("role"),
                          "email": (email[:1] + "***@" + email.split("@", 1)[-1]) if "@" in email else None}
    for path in ["/api/farms", "/api/search?q=durian", "/api/search?q=durian&page=-1",
                 "/api/v1/notification/notifications", "/api/v1/notification/notifications/unread-count",
                 "/api/v1/harvest-batches/dispatch-summary?farmId=farm-khoa-luan-2026"]:
        call("GET", path, token)
    if auth.get("refreshToken"):
        status, refreshed = call("POST", "/api/auth/refresh", body={"refreshToken": auth["refreshToken"]})
        if status == 200 and refreshed.get("accessToken"):
            call("GET", "/api/users/me", refreshed["accessToken"])
    # Logout revokes all of this shared admin's refresh sessions, so it is deliberately not invoked.
    result["status"] = "AUTHENTICATED_ADMIN_READ_ONLY_PROBE_COMPLETED"
    return result


if __name__ == "__main__":
    try:
        print(json.dumps(probe(), indent=2))
    except Exception as exc:
        print(json.dumps({"status": "FAIL", "errorType": type(exc).__name__}))
        raise SystemExit(3)
