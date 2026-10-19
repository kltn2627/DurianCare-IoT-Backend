"""Safe authenticated demo/E2E harness for DurianCare.

This harness intentionally never prints credentials, bearer tokens, cookies,
refresh tokens, or request headers. It reads authentication context only from
environment variables supplied by the operator.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


API_BASE = os.getenv("DURIANCARE_E2E_API_URL", "http://localhost:8080").rstrip("/")
DEFAULT_TREE_CODE = os.getenv("DURIANCARE_E2E_TREE_CODE", "DC-T009")
ALLOW_WRITE = os.getenv("DURIANCARE_E2E_ALLOW_WRITE", "").lower() in {"1", "true", "yes"}


@dataclass
class ApiResult:
    method: str
    path: str
    status: int
    body: Any


class HarnessError(RuntimeError):
    pass


class BlockedAuthContext(HarnessError):
    pass


def main() -> int:
    report: dict[str, Any] = {
        "status": "BLOCKED_AUTH_CONTEXT",
        "apiBase": API_BASE,
        "treeCode": DEFAULT_TREE_CODE,
        "writeEnabled": ALLOW_WRITE,
        "steps": [],
        "identity": None,
        "ownership": None,
        "readOnlyAudit": None,
        "writePath": "NOT_TESTED",
    }

    try:
        token = resolve_access_token(report)
        identity = call("GET", "/api/users/me", token)
        report["steps"].append(step(identity, sanitize_user(identity.body)))
        user_id = identity.body.get("id") or identity.body.get("userId")
        role = identity.body.get("role")
        if not user_id:
            raise HarnessError("Identity response did not include user id")
        report["identity"] = {
            "userId": user_id,
            "email": mask_email(identity.body.get("email")),
            "role": role,
            "accountStatus": identity.body.get("accountStatus") or identity.body.get("status"),
        }

        audit = read_only_audit(token, report, user_id)
        report["ownership"] = audit["ownership"]
        report["readOnlyAudit"] = audit
        report["status"] = "READ_ONLY_PASS"

        if ALLOW_WRITE:
            report["writePath"] = run_write_path(token, audit, report)
            report["status"] = "E2E_PASS" if report["writePath"] == "PASS" else "BLOCKED_WRITE_PATH"
        else:
            report["writePath"] = "BLOCKED_WRITE_PATH: DURIANCARE_E2E_ALLOW_WRITE is not enabled"

        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except BlockedAuthContext as exc:
        report["blocker"] = str(exc)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 2
    except HarnessError as exc:
        report["status"] = "FAIL"
        report["error"] = str(exc)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 3


def resolve_access_token(report: dict[str, Any]) -> str:
    token = os.getenv("DURIANCARE_E2E_ACCESS_TOKEN")
    if token:
        report["authSource"] = "DURIANCARE_E2E_ACCESS_TOKEN"
        return token

    email = os.getenv("DURIANCARE_E2E_EMAIL")
    password = os.getenv("DURIANCARE_E2E_PASSWORD")
    if not email or not password:
        raise BlockedAuthContext(
            "Missing auth context. Set DURIANCARE_E2E_ACCESS_TOKEN or "
            "DURIANCARE_E2E_EMAIL + DURIANCARE_E2E_PASSWORD in the local environment."
        )

    login = call(
        "POST",
        "/api/auth/login",
        None,
        {"email": email, "password": password},
    )
    report["steps"].append(step(login, {"email": mask_email(login.body.get("email")), "role": login.body.get("role")}))
    access_token = login.body.get("accessToken")
    if not access_token:
        raise HarnessError("Login response did not include access token")
    report["authSource"] = "DURIANCARE_E2E_EMAIL_PASSWORD"
    return access_token


def read_only_audit(token: str, report: dict[str, Any], user_id: str) -> dict[str, Any]:
    farms = call("GET", "/api/farms", token)
    farm_items = expect_list(farms.body, "/api/farms")
    report["steps"].append(step(farms, {"count": len(farm_items), "ids": [item.get("id") for item in farm_items]}))
    if not farm_items:
        raise HarnessError("Authenticated user has no farms")

    farm_id = os.getenv("DURIANCARE_E2E_FARM_ID") or farm_items[0].get("id")
    farm = call("GET", f"/api/farms/{quote(farm_id)}", token)
    report["steps"].append(step(farm, sanitize_keys(farm.body, ["id", "name", "zoneCount", "status"])))

    zones = call("GET", f"/api/farms/{quote(farm_id)}/zones", token)
    zone_items = expect_list(zones.body, f"/api/farms/{farm_id}/zones")
    report["steps"].append(step(zones, {"count": len(zone_items), "ids": [item.get("id") for item in zone_items]}))
    if not zone_items:
        raise HarnessError("Authenticated farm has no zones")

    target_tree = None
    target_zone = None
    all_zone_counts: list[dict[str, Any]] = []
    for zone in zone_items:
        zone_id = zone.get("id")
        trees = call("GET", f"/api/zones/{quote(zone_id)}/trees", token)
        tree_items = expect_list(trees.body, f"/api/zones/{zone_id}/trees")
        all_zone_counts.append({"zoneId": zone_id, "count": len(tree_items)})
        report["steps"].append(step(trees, {"zoneId": zone_id, "count": len(tree_items)}))
        for tree in tree_items:
            if tree.get("treeCode") == DEFAULT_TREE_CODE or tree.get("code") == DEFAULT_TREE_CODE:
                target_tree = tree
                target_zone = zone
                break
        if target_tree:
            break
    if not target_tree:
        raise HarnessError(f"Tree code {DEFAULT_TREE_CODE} was not found in owned zones")

    tree_id = target_tree.get("id")
    tree = call("GET", f"/api/trees/{quote(tree_id)}", token)
    report["steps"].append(step(tree, sanitize_tree(tree.body)))

    diagnoses = call("GET", f"/api/trees/{quote(tree_id)}/diagnoses?page=0&size=10", token)
    diagnosis_items = page_items(diagnoses.body)
    report["steps"].append(step(diagnoses, {"count": len(diagnosis_items), "latest": sanitize_diagnosis(diagnosis_items[0]) if diagnosis_items else None}))

    latest = call("GET", f"/api/trees/{quote(tree_id)}/diagnoses/latest", token)
    report["steps"].append(step(latest, sanitize_diagnosis(latest.body)))

    plans = call("GET", f"/api/trees/{quote(tree_id)}/care-plans", token)
    plan_items = expect_list(plans.body, f"/api/trees/{tree_id}/care-plans")
    report["steps"].append(step(plans, {"count": len(plan_items), "ids": [item.get("id") for item in plan_items]}))

    recommendation = safe_call("GET", f"/api/trees/{quote(tree_id)}/care-recommendation/latest", token)
    report["steps"].append(step(recommendation, sanitize_recommendation(recommendation.body) if 200 <= recommendation.status < 300 else {"notAvailable": True}))

    notifications = safe_call("GET", "/api/v1/notification/notifications?size=10", token)
    report["steps"].append(step(notifications, summarize_notifications(notifications.body) if 200 <= notifications.status < 300 else {"notAvailable": True}))

    return {
        "ownership": {
            "verifiedBy": "Gateway authenticated API returned user profile, owned farms, zones, and tree details without direct DB access",
            "userId": user_id,
            "farmId": farm_id,
            "zoneId": target_zone.get("id"),
            "treeId": tree_id,
            "treeCode": DEFAULT_TREE_CODE,
        },
        "farmCount": len(farm_items),
        "zoneCounts": all_zone_counts,
        "targetTree": sanitize_tree(tree.body),
        "latestDiagnosis": sanitize_diagnosis(latest.body),
        "carePlanCount": len(plan_items),
    }


def run_write_path(token: str, audit: dict[str, Any], report: dict[str, Any]) -> str:
    tree_id = audit["ownership"]["treeId"]
    latest_diagnosis = audit.get("latestDiagnosis") or {}
    diagnosis_id = latest_diagnosis.get("id")
    if not diagnosis_id:
        return "BLOCKED_WRITE_PATH: latest diagnosis id is missing"

    recommendation = call(
        "POST",
        f"/api/trees/{quote(tree_id)}/care-recommendation",
        token,
        {"diagnosisId": diagnosis_id},
    )
    report["steps"].append(step(recommendation, sanitize_recommendation(recommendation.body)))
    recommendation_id = recommendation.body.get("id")
    if not recommendation_id:
        return "BLOCKED_WRITE_PATH: recommendation id is missing"

    accepted = call(
        "POST",
        f"/api/trees/{quote(tree_id)}/care-recommendation/{quote(recommendation_id)}/accept",
        token,
        {},
    )
    report["steps"].append(step(accepted, sanitize_keys(accepted.body, ["id", "treeId", "status", "diagnosisId", "followUpDate"])))
    plan_id = accepted.body.get("id")
    if not plan_id:
        return "BLOCKED_WRITE_PATH: accepted care plan id is missing"

    tasks = call("GET", f"/api/care-plans/{quote(plan_id)}/tasks", token)
    task_items = expect_list(tasks.body, f"/api/care-plans/{plan_id}/tasks")
    report["steps"].append(step(tasks, {"count": len(task_items), "ids": [item.get("id") for item in task_items]}))
    return "PASS"


def call(method: str, path: str, token: str | None, body: Any = None) -> ApiResult:
    result = safe_call(method, path, token, body)
    if not 200 <= result.status < 300:
        raise HarnessError(f"{method} {path} returned HTTP {result.status}")
    return result


def safe_call(method: str, path: str, token: str | None, body: Any = None) -> ApiResult:
    url = f"{API_BASE}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode("utf-8", errors="replace")
            return ApiResult(method, path, response.status, parse_json(raw))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        return ApiResult(method, path, exc.code, parse_json(raw))
    except urllib.error.URLError as exc:
        raise HarnessError(f"{method} {path} failed to connect: {exc.reason}") from exc


def step(result: ApiResult, fields: Any) -> dict[str, Any]:
    return {
        "method": result.method,
        "path": result.path,
        "status": result.status,
        "fields": fields,
    }


def parse_json(raw: str) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_nonJson": raw[:200]}


def quote(value: Any) -> str:
    return urllib.parse.quote(str(value), safe="")


def expect_list(value: Any, path: str) -> list[dict[str, Any]]:
    if isinstance(value, list):
        return value
    raise HarnessError(f"{path} did not return a JSON list")


def page_items(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, dict) and isinstance(value.get("content"), list):
        return value["content"]
    if isinstance(value, list):
        return value
    return []


def sanitize_user(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    return {
        "id": value.get("id") or value.get("userId"),
        "email": mask_email(value.get("email")),
        "role": value.get("role"),
        "accountStatus": value.get("accountStatus") or value.get("status"),
        "fullName": value.get("fullName") or (value.get("profile") or {}).get("fullName"),
    }


def sanitize_tree(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    return sanitize_keys(value, ["id", "treeCode", "code", "farmId", "zoneId", "farmZoneId", "healthStatus", "diagnosisCount", "latestDiagnosisAt"])


def sanitize_diagnosis(value: Any) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    return sanitize_keys(value, ["id", "treeId", "diseaseCode", "diseaseName", "confidence", "source", "createdAt", "diagnosedAt"])


def sanitize_recommendation(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    return sanitize_keys(value, ["id", "treeId", "diagnosisId", "diseaseCode", "status", "source", "createdAt"])


def summarize_notifications(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        items = value.get("notifications") or value.get("content") or []
        return {"count": len(items), "total": value.get("totalElements") or value.get("total")}
    if isinstance(value, list):
        return {"count": len(value)}
    return {}


def sanitize_keys(value: dict[str, Any], keys: list[str]) -> dict[str, Any]:
    return {key: value.get(key) for key in keys if key in value}


def mask_email(value: Any) -> str | None:
    if not isinstance(value, str) or "@" not in value:
        return None
    local, domain = value.split("@", 1)
    prefix = local[:2] if len(local) >= 2 else local[:1]
    return f"{prefix}***@{domain}"


if __name__ == "__main__":
    sys.exit(main())
