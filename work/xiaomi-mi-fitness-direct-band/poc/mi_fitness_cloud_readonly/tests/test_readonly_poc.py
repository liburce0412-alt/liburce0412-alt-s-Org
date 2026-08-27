from __future__ import annotations

import json
import http.cookiejar
import sys
import unittest
import urllib.parse
import urllib.request
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import readonly_poc as protocol


FIXTURE_PATH = ROOT / "fixtures" / "protocol_vectors.json"


class ProtocolVectorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.fixture = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))

    def test_rc4_drop_1024_known_vector(self) -> None:
        key = bytes(range(32))
        plaintext = b"xiaomi-wire-protocol"
        self.assertEqual(
            protocol.rc4_crypt(key, plaintext).hex(),
            "ce0131835d9ad18c36d39e531a260c06eeaac74f",
        )

    def test_nonce_layout(self) -> None:
        nonce = protocol.generate_nonce(
            random_bytes=lambda size: bytes(range(size)),
            now_seconds=lambda: 42 * 60,
        )
        self.assertEqual(nonce.hex(), "00010203040506070000002a")

    def test_app_compatible_continuous_rc4_form(self) -> None:
        request = self.fixture["request"]
        actual = protocol.build_encrypted_form(
            request["method"],
            request["path"],
            request["payload"],
            self.fixture["ssecurity_b64"],
            protocol._b64decode(self.fixture["nonce_b64"], field="nonce"),
        )
        self.assertEqual(actual, request["expected_form"])

    def test_decrypt_synthetic_response(self) -> None:
        response = self.fixture["response"]
        actual = protocol.decrypt_response(
            response["ciphertext_b64"],
            self.fixture["ssecurity_b64"],
            self.fixture["nonce_b64"],
        )
        self.assertEqual(actual, response["plaintext"])
        self.assertEqual(
            set(actual["result"]), {"data_list", "has_more", "next_key"}
        )

    def test_login_prefix_and_client_sign(self) -> None:
        login = self.fixture["login"]
        payload = protocol.parse_login_payload(login["text"])
        self.assertEqual(payload["userId"], 12345)
        self.assertEqual(
            protocol.build_client_sign(payload["nonce"], payload["ssecurity"]),
            login["expected_client_sign"],
        )

    def test_synthetic_pass_token_exchange_scopes_credentials(self) -> None:
        class FakeResponse:
            def __init__(self, body: bytes, final_url: str) -> None:
                self._body = body
                self._final_url = final_url
                self.headers: dict[str, str] = {}

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, traceback) -> bool:
                return False

            def read(self) -> bytes:
                return self._body

            def geturl(self) -> str:
                return self._final_url

        class FakeOpener:
            def __init__(self, responses: list[FakeResponse]) -> None:
                self.responses = responses
                self.requests: list[urllib.request.Request] = []

            def open(self, request, timeout=0):
                self.requests.append(request)
                return self.responses.pop(0)

        login = self.fixture["login"]
        final_url = (
            "https://sts-hlth.io.mi.com/healthapp/sts?"
            "serviceToken=synthetic-service-token"
        )
        opener = FakeOpener(
            [
                FakeResponse(login["text"].encode("utf-8"), protocol.LOGIN_URL),
                FakeResponse(b"ok", final_url),
            ]
        )
        jar = http.cookiejar.CookieJar()
        session = protocol.exchange_pass_token(
            opener,
            jar,
            "12345",
            "synthetic-original-pass-token",
            device_id="synthetic-device-id",
        )

        self.assertEqual(len(opener.requests), 2)
        first_query = urllib.parse.parse_qs(
            urllib.parse.urlsplit(opener.requests[0].full_url).query
        )
        self.assertEqual(first_query["sid"], ["miothealth"])
        self.assertEqual(first_query["_json"], ["true"])
        self.assertEqual(first_query["appName"], ["com.mi.health"])
        self.assertEqual(first_query["_locale"], ["zh_CN"])
        first_cookie = opener.requests[0].get_header("Cookie") or ""
        self.assertIn("userId=12345", first_cookie)
        self.assertIn("passToken=synthetic-original-pass-token", first_cookie)
        self.assertIn("deviceId=synthetic-device-id", first_cookie)
        second_cookie = opener.requests[1].get_header("Cookie") or ""
        self.assertNotIn("passToken", second_cookie)
        self.assertIn("deviceId=synthetic-device-id", second_cookie)
        location_query = urllib.parse.parse_qs(
            urllib.parse.urlsplit(opener.requests[1].full_url).query
        )
        self.assertEqual(location_query["clientSign"], [login["expected_client_sign"]])
        self.assertEqual(location_query["_userIdNeedEncrypt"], ["true"])
        self.assertEqual(session.service_token, "synthetic-service-token")
        self.assertTrue(session.token_refreshed)
        self.assertNotIn("pass_token", session.__dataclass_fields__)
        self.assertEqual(
            protocol._api_cookie_header(session),
            "cUserId=fake-c-user; serviceToken=synthetic-service-token; locale=zh_cn",
        )

    def test_summary_does_not_emit_raw_health_values(self) -> None:
        response = self.fixture["response"]["plaintext"]
        result = protocol.parse_result_envelope(response)
        summary = protocol.summarize_result(
            "steps", self.fixture["request"]["path"], result
        )
        rendered = json.dumps(summary)
        self.assertEqual(summary["record_count"], 1)
        self.assertNotIn("3210", rendered)
        self.assertNotIn("distance", rendered)
        self.assertNotIn("fake-refreshed-token", rendered)


class SafetyBoundaryTests(unittest.TestCase):
    def test_region_is_allowlisted(self) -> None:
        self.assertEqual(protocol.region_base_url("cn"), "https://hlth.io.mi.com")
        self.assertEqual(protocol.region_base_url("de"), "https://de.hlth.io.mi.com")
        for invalid in ("attacker.example", "../cn", "https://hlth.io.mi.com", ""):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                protocol.region_base_url(invalid)

    def test_write_endpoints_and_post_are_unrepresentable(self) -> None:
        with self.assertRaises(ValueError):
            protocol.ReadRequest("POST", "/app/v1/data/get_fitness_data_by_time", {})
        with self.assertRaises(ValueError):
            protocol.ReadRequest("GET", "/app/v1/data/up_fitness_data", {})
        with self.assertRaises(ValueError):
            protocol.build_encrypted_form(
                "POST",
                "/app/v1/data/up_fitness_data",
                {"data": "fake"},
                "c2VjdXJpdHk=",
                b"0123456789ab",
            )

    def test_metric_routes_are_read_only(self) -> None:
        for metric in sorted(protocol.FITNESS_KEYS | {"workouts"}):
            with self.subTest(metric=metric):
                request = protocol.build_read_request(metric)
                self.assertEqual(request.method, "GET")
                self.assertIn(request.path, protocol.READ_ONLY_PATHS)

    def test_app_v1_payload_models_are_exact(self) -> None:
        with mock.patch.object(protocol, "_date_window", return_value=(100, 200)):
            fitness = protocol.build_read_request("steps")
            workouts = protocol.build_read_request("workouts")
        self.assertEqual(
            fitness.payload,
            {
                "key": "steps",
                "start_time": 100,
                "end_time": 200,
                "reverse": True,
                "next_key": "",
            },
        )
        self.assertNotIn("relative_uid", fitness.payload)
        self.assertNotIn("limit", fitness.payload)
        self.assertEqual(
            workouts.payload,
            {
                "category": "",
                "start_time": 100,
                "end_time": 200,
                "reverse": True,
                "next_key": "",
                "limit": 50,
            },
        )

    def test_health_cookie_header_matches_official_interceptors(self) -> None:
        session = protocol.SessionMaterial(
            user_id="12345",
            c_user_id="synthetic-c-user",
            service_token="synthetic-service-token",
            ssecurity_b64="c2VjdXJpdHk=",
            device_id="synthetic-device",
            token_refreshed=False,
        )
        self.assertEqual(
            protocol._api_cookie_header(session),
            "cUserId=synthetic-c-user; serviceToken=synthetic-service-token; locale=zh_cn",
        )

    def test_sts_security_query_values_replace_server_duplicates(self) -> None:
        actual = protocol._append_query_values(
            "https://sts-hlth.io.mi.com/healthapp/sts?sid=miothealth&clientSign=old",
            {"clientSign": "new", "_userIdNeedEncrypt": "true"},
        )
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(actual).query)
        self.assertEqual(query["sid"], ["miothealth"])
        self.assertEqual(query["clientSign"], ["new"])
        self.assertEqual(query["_userIdNeedEncrypt"], ["true"])

    def test_success_envelope_accepts_app_codes_zero_and_200(self) -> None:
        self.assertEqual(protocol.parse_result_envelope({"code": 0, "result": {}}), {})
        self.assertEqual(protocol.parse_result_envelope({"code": 200, "result": {}}), {})

    def test_sensitive_login_cookies_are_removed_before_health_use(self) -> None:
        jar = http.cookiejar.CookieJar()
        for name in ("userId", "passToken", "deviceId"):
            protocol._set_cookie(jar, name, f"synthetic-{name}", ".mi.com")
        protocol._set_cookie(jar, "cUserId", "synthetic-c-user", ".mi.com")
        protocol._set_cookie(jar, "serviceToken", "synthetic-service", ".mi.com")
        protocol._clear_sensitive_login_cookies(jar)
        request = urllib.request.Request("https://hlth.io.mi.com/app/v1/data/test")
        jar.add_cookie_header(request)
        header = request.get_header("Cookie") or ""
        self.assertIn("cUserId=synthetic-c-user", header)
        self.assertIn("serviceToken=synthetic-service", header)
        self.assertNotIn("passToken", header)
        self.assertNotIn("deviceId", header)
        self.assertNotIn("userId=", header)

    def test_redirect_hosts_are_strictly_validated(self) -> None:
        for valid in (
            "https://account.xiaomi.com/pass/serviceLogin",
            "https://sts-hlth.io.mi.com/healthapp/sts",
        ):
            protocol._validate_xiaomi_redirect(valid)
        for invalid in (
            "http://account.xiaomi.com/pass/serviceLogin",
            "https://mi.com.evil.example/",
            "https://example.com/",
        ):
            with self.subTest(invalid=invalid), self.assertRaises(
                protocol.AuthenticationError
            ):
                protocol._validate_xiaomi_redirect(invalid)

    def test_cookie_values_reject_header_injection(self) -> None:
        with self.assertRaises(protocol.AuthenticationError):
            protocol._cookie_header({"passToken": "synthetic\r\nX-Leak: yes"})

    def test_xiaomi_opener_disables_environment_proxies(self) -> None:
        # A default urllib opener calls getproxies(). Supplying ProxyHandler({})
        # suppresses that default even though an empty handler has no callbacks
        # and is therefore absent from opener.handlers.
        with mock.patch.object(
            urllib.request,
            "getproxies",
            side_effect=AssertionError("environment proxy lookup was attempted"),
        ):
            protocol.build_xiaomi_opener()


if __name__ == "__main__":
    unittest.main()
