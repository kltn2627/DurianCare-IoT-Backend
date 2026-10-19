"""Gateway E2E for operator-provided isolated fixtures. No fixture/account generation."""
from __future__ import annotations

import json
import os
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlencode, urlparse

import demo_e2e_harness as api


def run() -> tuple[dict, int]:
    report = {"status": "BLOCKED_AUTH_CONTEXT", "steps": [], "identity": None}
    try:
        base = urlparse(api.API_BASE)
        if base.scheme != "http" or base.hostname not in {"localhost", "127.0.0.1", "::1"} or base.username or base.password:
            return {**report, "status": "BLOCKED_ENVIRONMENT"}, 2
        token = api.resolve_access_token(report)

        def request(method, path, body=None):
            result = api.safe_call(method, path, token, body)
            report["steps"].append({"method": method, "path": path, "status": result.status})
            if not 200 <= result.status < 300:
                raise api.HarnessError("Unexpected HTTP status")
            return result.body

        identity = request("GET", "/api/users/me")
        user_id = identity.get("id") or identity.get("userId")
        if not user_id or identity.get("role") != "FARMER":
            return {**report, "status": "BLOCKED_IDENTITY"}, 2
        report["identity"] = api.sanitize_user(identity)
        manifest_path = os.getenv("DURIANCARE_DISPATCH_E2E_MANIFEST")
        if not manifest_path:
            return {**report, "status": "BLOCKED_ISOLATED_FIXTURE"}, 2
        manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
        farm_id, lot_id = manifest["farmId"], manifest["harvestBatchId"]
        # Explicit names prevent accidental use of the existing thesis demo lots/farm.
        if manifest.get("approvedIsolatedFixture") is not True or not all(
            isinstance(value, str) and value.startswith("dispatch-e2e-") for value in (farm_id, lot_id)
        ):
            return {**report, "status": "BLOCKED_ISOLATED_FIXTURE"}, 2
        farms = request("GET", "/api/farms")
        if not isinstance(farms, list) or farm_id not in {f.get("id") for f in farms}:
            return {**report, "status": "BLOCKED_OWNERSHIP"}, 2
        lot = request("GET", f"/api/v1/harvest-batches/{api.quote(lot_id)}")
        if lot.get("farmId") != farm_id or lot.get("status") != "HARVESTED":
            return {**report, "status": "BLOCKED_LOT"}, 2
        summary_path = "/api/v1/harvest-batches/dispatch-summary?" + urlencode({
            "farmId": farm_id, "cultivationSeasonId": lot["cultivationSeasonId"]})
        before = request("GET", summary_path)
        if before.get("status") != "NO_VERIFIED_DATA" or before.get("quantitiesByUnit") is not None:
            return {**report, "status": "BLOCKED_NONEMPTY_FIXTURE"}, 2
        if os.getenv("DURIANCARE_DISPATCH_E2E_ALLOW_WRITE") != "1":
            return {**report, "status": "READ_ONLY_PASS", "writePath": "BLOCKED_WRITE_PATH"}, 0
        body = manifest["recordRequest"]
        endpoint = f"/api/v1/harvest-batches/{api.quote(lot_id)}/dispatch"
        record = request("POST", endpoint, body)
        if record.get("confirmedAt") is not None or record.get("recordedBy") != user_id:
            raise api.HarnessError("Unexpected persisted pending dossier identity/state")
        pending = request("GET", summary_path)
        if pending.get("status") != "NO_VERIFIED_DATA" or pending.get("quantitiesByUnit") is not None:
            raise api.HarnessError("Pending dossier contributed to totals")
        confirmed = request("POST", endpoint + "/confirm", {"sourceDocumentsChecked": True})
        persisted = request("GET", endpoint)
        if not confirmed.get("confirmedAt") or persisted.get("confirmedBy") != user_id:
            raise api.HarnessError("Confirmation was not persisted for authenticated actor")
        if persisted.get("farmId") != farm_id or persisted.get("plotId") != lot.get("plotId") or persisted.get("cultivationSeasonId") != lot.get("cultivationSeasonId"):
            raise api.HarnessError("Persisted scope mismatch")
        summary = request("GET", summary_path)
        if summary.get("status") != "OPERATOR_CONFIRMED_RECORDS" or summary.get("confirmedLotCount") != 1:
            raise api.HarnessError("Confirmed dossier missing from summary")
        expected = body["evidence"]
        actual = summary["quantitiesByUnit"][expected["quantityUnit"]]["shipped"]
        if Decimal(str(actual)) != Decimal(str(expected["shippedQuantity"])):
            raise api.HarnessError("Persisted shipped total does not match evidence")
        replay = request("POST", endpoint, body)
        if replay.get("id") != record.get("id"):
            raise api.HarnessError("Retry changed dossier identity")
        return {**report, "status": "LIVE_ISOLATED_FIXTURE_E2E_PASS"}, 0
    except api.BlockedAuthContext:
        return report, 2
    except Exception as exc:
        # Do not expose HTTP bodies, manifest contents, request headers or credentials.
        return {**report, "status": "FAIL", "errorType": type(exc).__name__}, 3


if __name__ == "__main__":
    result, exit_code = run()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(exit_code)
