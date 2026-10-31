import os
import unittest
from unittest.mock import patch

import dispatch_e2e_harness as harness
import demo_e2e_harness as api


class DispatchHarnessSafetyTest(unittest.TestCase):
    def test_missing_credentials_stops_before_http_or_writes(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(api, "API_BASE", "http://localhost:8080"), \
                patch.object(api, "safe_call") as http:
            result, code = harness.run()
        self.assertEqual((result["status"], code), ("BLOCKED_AUTH_CONTEXT", 2))
        http.assert_not_called()

    def test_nonlocal_target_stops_before_reading_auth(self):
        with patch.object(api, "API_BASE", "https://example.invalid"), \
                patch.object(api, "resolve_access_token") as auth:
            result, code = harness.run()
        self.assertEqual((result["status"], code), ("BLOCKED_ENVIRONMENT", 2))
        auth.assert_not_called()

    def test_missing_fixture_after_identity_does_not_start_write_path(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(api, "API_BASE", "http://localhost:8080"), \
                patch.object(api, "resolve_access_token", return_value=object()), \
                patch.object(api, "safe_call", return_value=api.ApiResult("GET", "/api/users/me", 200,
                             {"id": "fixture-user", "role": "FARMER"})) as http:
            result, code = harness.run()
        self.assertEqual((result["status"], code), ("BLOCKED_ISOLATED_FIXTURE", 2))
        self.assertEqual(http.call_count, 1)
        self.assertEqual(http.call_args.args[0], "GET")


if __name__ == "__main__":
    unittest.main()
