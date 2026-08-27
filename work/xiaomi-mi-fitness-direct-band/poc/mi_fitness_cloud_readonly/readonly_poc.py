"""Minimal read-only Xiaomi Mi Fitness cloud protocol proof of concept.

The default execution path is offline and only validates a synthetic fixture.
Live mode performs Xiaomi Account session exchange plus one allowlisted GET.
It never persists credentials and never prints raw health records.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import getpass
import hashlib
import http.cookiejar
import json
import os
import re
import secrets
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, time as datetime_time, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Mapping


LOGIN_PREFIX = "&&&START&&&"
LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin"
SERVICE_SID = "miothealth"
LOGIN_APP_NAME = "com.mi.health"
LOGIN_LOCALE = "zh_CN"
API_LOCALE = "zh_cn"
READ_ONLY_PATHS = frozenset(
    {
        "/app/v1/data/get_fitness_data_by_time",
        "/app/v1/data/get_sport_records_by_time",
    }
)
FITNESS_KEYS = frozenset(
    {
        "steps",
        "sleep",
        "heart_rate",
        "spo2",
        "calories",
        "valid_stand",
        "intensity",
        "weight",
        "blood_pressure",
        "stress",
    }
)
KNOWN_REGIONS = frozenset({"cn", "ru", "de", "i2", "sg", "us"})
RETRYABLE_HTTP_STATUS = frozenset({429, 500, 502, 503, 504})
LOGIN_USER_AGENT = (
    "Dalvik/2.1.0 (Linux; U; Android 16) APP/mi.health APPV/358000 "
    "CPN/com.mi.health PassportSDK/"
)
API_USER_AGENT = "Android-16-3.58.0-CampusAI-ReadOnly-PoC"


class PocError(RuntimeError):
    """Base error whose message is safe to show without raw responses."""


class AuthenticationError(PocError):
    pass


class ProtocolError(PocError):
    pass


class NetworkError(PocError):
    pass


@dataclass(frozen=True)
class SessionMaterial:
    user_id: str
    c_user_id: str
    service_token: str
    ssecurity_b64: str
    device_id: str
    token_refreshed: bool


@dataclass(frozen=True)
class ReadRequest:
    method: str
    path: str
    payload: dict[str, Any]

    def __post_init__(self) -> None:
        if self.method != "GET" or self.path not in READ_ONLY_PATHS:
            raise ValueError("Only allowlisted read-only GET endpoints are permitted")


def _b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def _b64decode(value: str, *, field: str) -> bytes:
    try:
        decoded = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError, TypeError) as exc:
        raise ProtocolError(f"{field} is not valid base64") from exc
    if not decoded:
        raise ProtocolError(f"{field} is empty")
    return decoded


def rc4_crypt(key: bytes, payload: bytes, *, drop: int = 1024) -> bytes:
    """RC4 with the first 1024 keystream bytes discarded."""
    if not key:
        raise ValueError("RC4 key must not be empty")
    state = list(range(256))
    state_index = 0
    for index in range(256):
        state_index = (state_index + state[index] + key[index % len(key)]) & 0xFF
        state[index], state[state_index] = state[state_index], state[index]

    index = 0
    state_index = 0

    def next_byte() -> int:
        nonlocal index, state_index
        index = (index + 1) & 0xFF
        state_index = (state_index + state[index]) & 0xFF
        state[index], state[state_index] = state[state_index], state[index]
        return state[(state[index] + state[state_index]) & 0xFF]

    for _ in range(drop):
        next_byte()
    return bytes(value ^ next_byte() for value in payload)


def generate_nonce(
    *,
    random_bytes: Callable[[int], bytes] = secrets.token_bytes,
    now_seconds: Callable[[], float] = time.time,
) -> bytes:
    """random(8) || floor(unix_time / 60) as unsigned big-endian uint32."""
    random_part = random_bytes(8)
    if len(random_part) != 8:
        raise ValueError("random source must return exactly 8 bytes")
    return random_part + struct.pack(">I", int(now_seconds() // 60))


def signed_nonce(ssecurity: bytes, nonce: bytes) -> bytes:
    return hashlib.sha256(ssecurity + nonce).digest()


def _signature_message(
    method: str,
    path: str,
    values: Mapping[str, str],
    signed_nonce_bytes: bytes,
) -> str:
    normalized_path = path if path.startswith("/") else f"/{path}"
    parts = [method.upper(), normalized_path]
    parts.extend(f"{key}={values[key]}" for key in sorted(values))
    parts.append(_b64(signed_nonce_bytes))
    return "&".join(parts)


def generate_signature(
    method: str,
    path: str,
    values: Mapping[str, str],
    signed_nonce_bytes: bytes,
) -> str:
    message = _signature_message(method, path, values, signed_nonce_bytes)
    return _b64(hashlib.sha1(message.encode("utf-8")).digest())


def build_encrypted_form(
    method: str,
    path: str,
    payload: Mapping[str, Any],
    ssecurity_b64: str,
    nonce: bytes | None = None,
) -> dict[str, str]:
    """Build the app-compatible form using one continuous sorted-field RC4 stream."""
    if method.upper() != "GET" or path not in READ_ONLY_PATHS:
        raise ValueError("Only allowlisted read-only GET endpoints are permitted")
    nonce_bytes = nonce or generate_nonce()
    security = _b64decode(ssecurity_b64, field="ssecurity")
    key = signed_nonce(security, nonce_bytes)
    plaintext_values = {
        "data": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
    }
    plaintext_values["rc4_hash__"] = generate_signature(
        method, path, plaintext_values, key
    )

    entries = sorted(plaintext_values.items())
    encoded_values = [(name, value.encode("utf-8")) for name, value in entries]
    encrypted_stream = rc4_crypt(key, b"".join(value for _, value in encoded_values))
    encrypted: dict[str, str] = {}
    position = 0
    for name, value in encoded_values:
        encrypted[name] = _b64(encrypted_stream[position : position + len(value)])
        position += len(value)
    encrypted["signature"] = generate_signature(method, path, encrypted, key)
    encrypted["_nonce"] = _b64(nonce_bytes)
    return encrypted


def decrypt_response(ciphertext_b64: str, ssecurity_b64: str, nonce_b64: str) -> dict[str, Any]:
    security = _b64decode(ssecurity_b64, field="ssecurity")
    nonce = _b64decode(nonce_b64, field="nonce")
    ciphertext = _b64decode(ciphertext_b64.strip(), field="response")
    plaintext = rc4_crypt(signed_nonce(security, nonce), ciphertext)
    try:
        parsed = json.loads(plaintext.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProtocolError("Encrypted response could not be decoded") from exc
    if not isinstance(parsed, dict):
        raise ProtocolError("Encrypted response must contain a JSON object")
    return parsed


def parse_login_payload(text: str) -> dict[str, Any]:
    if not text.startswith(LOGIN_PREFIX):
        raise ProtocolError("Unexpected Xiaomi login response prefix")
    try:
        parsed = json.loads(text[len(LOGIN_PREFIX) :])
    except json.JSONDecodeError as exc:
        raise ProtocolError("Malformed Xiaomi login response") from exc
    if not isinstance(parsed, dict):
        raise ProtocolError("Malformed Xiaomi login response")
    return parsed


def build_client_sign(login_nonce: str, ssecurity_b64: str) -> str:
    message = f"nonce={login_nonce}&{ssecurity_b64}"
    return _b64(hashlib.sha1(message.encode("utf-8")).digest())


def region_base_url(region: str) -> str:
    normalized = region.strip().lower()
    if normalized not in KNOWN_REGIONS:
        raise ValueError(f"Unsupported region label: {normalized or '<empty>'}")
    if normalized == "cn":
        return "https://hlth.io.mi.com"
    return f"https://{normalized}.hlth.io.mi.com"


def _fixed_timezone(utc_offset: str) -> timezone:
    match = re.fullmatch(r"([+-])(0\d|1\d|2[0-3]):([0-5]\d)", utc_offset)
    if not match:
        raise ValueError("utc_offset must use ±HH:MM")
    direction = 1 if match.group(1) == "+" else -1
    delta = timedelta(hours=int(match.group(2)), minutes=int(match.group(3)))
    return timezone(direction * delta)


def _date_window(days: int, utc_offset: str) -> tuple[int, int]:
    if not 1 <= days <= 30:
        raise ValueError("days must be between 1 and 30 for this PoC")
    zone = _fixed_timezone(utc_offset)
    today = datetime.now(zone).date()
    start_day = today - timedelta(days=days - 1)
    start = datetime.combine(start_day, datetime_time.min, zone)
    end = datetime.combine(today + timedelta(days=1), datetime_time.min, zone) - timedelta(
        seconds=1
    )
    return int(start.timestamp()), int(end.timestamp())


def build_read_request(
    metric: str,
    *,
    days: int = 1,
    utc_offset: str = "+08:00",
) -> ReadRequest:
    start, end = _date_window(days, utc_offset)
    if metric in FITNESS_KEYS:
        return ReadRequest(
            "GET",
            "/app/v1/data/get_fitness_data_by_time",
            {
                "key": metric,
                "start_time": start,
                "end_time": end,
                "reverse": True,
                "next_key": "",
            },
        )
    if metric == "workouts":
        return ReadRequest(
            "GET",
            "/app/v1/data/get_sport_records_by_time",
            {
                "category": "",
                "start_time": start,
                "end_time": end,
                "reverse": True,
                "next_key": "",
                "limit": 50,
            },
        )
    raise ValueError(f"Unsupported read-only metric: {metric}")


def parse_result_envelope(body: Mapping[str, Any]) -> dict[str, Any]:
    try:
        code = int(body.get("code", -1))
    except (TypeError, ValueError) as exc:
        raise ProtocolError("Mi Fitness response code is invalid") from exc
    if code not in {0, 200}:
        raise ProtocolError(f"Mi Fitness returned business error code {code}")
    result = body.get("result", {})
    if not isinstance(result, dict):
        raise ProtocolError("Mi Fitness result must be an object")
    return result


def summarize_result(metric: str, path: str, result: Mapping[str, Any]) -> dict[str, Any]:
    list_key = "sport_records" if metric == "workouts" else "data_list"
    records = result.get(list_key, [])
    if not isinstance(records, list):
        raise ProtocolError(f"Mi Fitness {list_key} must be a list")
    return {
        "status": "ok",
        "mode": "read-only",
        "metric": metric,
        "endpoint": path,
        "record_count": len(records),
        "has_more": result.get("has_more") is True,
        "cursor_present": bool(result.get("next_key")),
    }


def _cookie_header(values: Mapping[str, str]) -> str:
    """Serialize trusted cookie names after rejecting header-control characters."""
    parts: list[str] = []
    for name, value in values.items():
        if not value:
            raise AuthenticationError(f"{name} is required")
        if any(character in value for character in ("\r", "\n", ";")):
            raise AuthenticationError(f"{name} contains invalid cookie characters")
        parts.append(f"{name}={value}")
    return "; ".join(parts)


def _api_cookie_header(session: SessionMaterial) -> str:
    # VerifyToken injects the token pair; ParameterInterceptor appends locale.
    return _cookie_header(
        {
            "cUserId": session.c_user_id,
            "serviceToken": session.service_token,
            "locale": API_LOCALE,
        }
    )


def _set_cookie(
    jar: http.cookiejar.CookieJar,
    name: str,
    value: str,
    domain: str,
) -> None:
    jar.set_cookie(
        http.cookiejar.Cookie(
            version=0,
            name=name,
            value=value,
            port=None,
            port_specified=False,
            domain=domain,
            domain_specified=True,
            domain_initial_dot=domain.startswith("."),
            path="/",
            path_specified=True,
            secure=True,
            expires=None,
            discard=True,
            comment=None,
            comment_url=None,
            rest={"HttpOnly": None},
            rfc2109=False,
        )
    )


def _cookie_value(jar: http.cookiejar.CookieJar, name: str) -> str:
    for cookie in jar:
        if cookie.name == name and cookie.value:
            return cookie.value
    return ""


def _clear_sensitive_login_cookies(jar: http.cookiejar.CookieJar) -> None:
    sensitive = {"userId", "passToken", "deviceId"}
    for cookie in list(jar):
        if cookie.name in sensitive:
            jar.clear(cookie.domain, cookie.path, cookie.name)


def _append_query_values(url: str, values: Mapping[str, str]) -> str:
    parsed = urllib.parse.urlsplit(url)
    query = [
        (name, value)
        for name, value in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
        if name not in values
    ]
    query.extend(values.items())
    return urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, parsed.path, urllib.parse.urlencode(query), parsed.fragment)
    )


def _read_url(
    opener: urllib.request.OpenerDirector,
    url: str,
    *,
    headers: Mapping[str, str],
    timeout: float = 30.0,
    attempts: int = 3,
) -> tuple[bytes, str, Mapping[str, str]]:
    last_error: BaseException | None = None
    for attempt in range(attempts):
        request = urllib.request.Request(url, headers=dict(headers), method="GET")
        try:
            with opener.open(request, timeout=timeout) as response:
                return response.read(), response.geturl(), response.headers
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code not in RETRYABLE_HTTP_STATUS or attempt + 1 == attempts:
                if exc.code in {401, 403}:
                    raise AuthenticationError(f"Xiaomi authentication failed (HTTP {exc.code})") from exc
                raise NetworkError(f"Xiaomi request failed (HTTP {exc.code})") from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last_error = exc
            if attempt + 1 == attempts:
                raise NetworkError("Xiaomi network request failed") from exc
        time.sleep(0.5 * (2**attempt))
    raise NetworkError("Xiaomi network request failed") from last_error


def _validate_xiaomi_redirect(location: str) -> None:
    parsed = urllib.parse.urlsplit(location)
    hostname = (parsed.hostname or "").lower()
    allowed = any(
        hostname == suffix or hostname.endswith(f".{suffix}")
        for suffix in ("xiaomi.com", "mi.com")
    )
    if parsed.scheme != "https" or not allowed:
        raise AuthenticationError("Xiaomi returned an unexpected redirect host")


class XiaomiRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Follow only Xiaomi HTTPS redirects and never forward an explicit Cookie header."""

    def redirect_request(
        self,
        req: urllib.request.Request,
        fp: Any,
        code: int,
        msg: str,
        headers: Mapping[str, str],
        newurl: str,
    ) -> urllib.request.Request | None:
        _validate_xiaomi_redirect(newurl)
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None:
            redirected.remove_header("Cookie")
            redirected.remove_header("cookie")
        return redirected


def build_xiaomi_opener(
    jar: http.cookiejar.CookieJar | None = None,
) -> urllib.request.OpenerDirector:
    # Do not inherit HTTP(S)_PROXY for a flow carrying a long-lived passToken.
    handlers: list[Any] = [urllib.request.ProxyHandler({}), XiaomiRedirectHandler()]
    if jar is not None:
        handlers.append(urllib.request.HTTPCookieProcessor(jar))
    return urllib.request.build_opener(*handlers)


def exchange_pass_token(
    opener: urllib.request.OpenerDirector,
    jar: http.cookiejar.CookieJar,
    user_id: str,
    pass_token: str,
    *,
    device_id: str | None = None,
) -> SessionMaterial:
    device = device_id or f"an_{secrets.token_hex(16)}"
    login_query = urllib.parse.urlencode(
        {
            "sid": SERVICE_SID,
            "_json": "true",
            "appName": LOGIN_APP_NAME,
            "_locale": LOGIN_LOCALE,
        }
    )
    body, _, login_headers = _read_url(
        opener,
        f"{LOGIN_URL}?{login_query}",
        headers={
            "User-Agent": LOGIN_USER_AGENT,
            # Scope long-lived credentials to this account request. They are
            # deliberately never inserted as broad .mi.com cookies.
            "Cookie": _cookie_header(
                {"userId": user_id, "passToken": pass_token, "deviceId": device}
            ),
        },
    )
    payload = parse_login_payload(body.decode("utf-8"))
    if "location" not in payload:
        raise AuthenticationError("Xiaomi login response is missing session fields")

    extension: dict[str, Any] = {}
    extension_text = str(
        login_headers.get("Extension-Pragma", "")
        or login_headers.get("extension-pragma", "")
        or login_headers.get("Extension_Pragama", "")
        or login_headers.get("extension_pragma", "")
    )
    if extension_text:
        try:
            candidate = json.loads(extension_text)
        except json.JSONDecodeError as exc:
            raise ProtocolError("Malformed Xiaomi login security header") from exc
        if isinstance(candidate, dict):
            extension = candidate

    next_user_id = str(payload.get("userId") or user_id)
    ssecurity_b64 = str(payload.get("ssecurity") or extension.get("ssecurity") or "")
    _b64decode(ssecurity_b64, field="ssecurity")
    c_user_id = str(payload.get("cUserId") or _cookie_value(jar, "cUserId"))
    if not c_user_id:
        raise AuthenticationError("Xiaomi login did not return an encrypted user ID")
    next_pass_token = str(
        payload.get("passToken") or _cookie_value(jar, "passToken") or pass_token
    )
    location = str(payload["location"])
    _validate_xiaomi_redirect(location)

    login_nonce = str(payload.get("nonce") or extension.get("nonce") or "")
    if not login_nonce:
        raise AuthenticationError("Xiaomi login response is missing the STS nonce")
    location = _append_query_values(
        location,
        {
            "clientSign": build_client_sign(login_nonce, ssecurity_b64),
            "_userIdNeedEncrypt": "true",
        },
    )
    # The AccountSDK sends its encrypted user ID and ephemeral device ID to
    # the validated STS location as well. Keep this explicit so no unrelated
    # cookie from the account jar is needed for the request.
    _, final_url, headers = _read_url(
        opener,
        location,
        headers={
            "User-Agent": LOGIN_USER_AGENT,
            "Cookie": _cookie_header({"cUserId": c_user_id, "deviceId": device}),
        },
    )
    service_token = _cookie_value(jar, f"{SERVICE_SID}_serviceToken") or _cookie_value(
        jar, "serviceToken"
    )
    if not service_token:
        candidates = [final_url, str(headers.get("Location", ""))]
        for candidate in candidates:
            values = urllib.parse.parse_qs(urllib.parse.urlsplit(candidate).query)
            for name in (f"{SERVICE_SID}_serviceToken", "serviceToken"):
                if values.get(name):
                    service_token = values[name][0]
                    break
            if service_token:
                break
    if not service_token:
        raise AuthenticationError("Xiaomi login did not return a service token")

    _set_cookie(jar, "serviceToken", service_token, ".mi.com")

    # Account-scoped identity cookies are unnecessary after exchange. Remove
    # them before returning and do not reuse this jar for the health API.
    _clear_sensitive_login_cookies(jar)

    return SessionMaterial(
        user_id=next_user_id,
        c_user_id=c_user_id,
        service_token=service_token,
        ssecurity_b64=ssecurity_b64,
        device_id=device,
        token_refreshed=next_pass_token != pass_token,
    )


def fetch_once(
    opener: urllib.request.OpenerDirector,
    session: SessionMaterial,
    request: ReadRequest,
    *,
    region: str,
) -> dict[str, Any]:
    encrypted = build_encrypted_form(
        request.method,
        request.path,
        request.payload,
        session.ssecurity_b64,
    )
    nonce = encrypted["_nonce"]
    query = urllib.parse.urlencode(encrypted)
    url = f"{region_base_url(region)}{request.path}?{query}"
    ciphertext, _, _ = _read_url(
        opener,
        url,
        headers={
            "User-Agent": API_USER_AGENT,
            "region_tag": region,
            "handleparams": "true",
            "Cookie": _api_cookie_header(session),
        },
        attempts=1,
    )
    return decrypt_response(ciphertext.decode("ascii"), session.ssecurity_b64, nonce)


def run_offline_fixture(path: Path) -> dict[str, Any]:
    fixture = json.loads(path.read_text(encoding="utf-8"))
    response = fixture["response"]
    body = decrypt_response(
        response["ciphertext_b64"], fixture["ssecurity_b64"], fixture["nonce_b64"]
    )
    result = parse_result_envelope(body)
    request = fixture["request"]
    summary = summarize_result(request["metric"], request["path"], result)
    summary["mode"] = "offline-vector"
    return summary


def run_live(args: argparse.Namespace) -> dict[str, Any]:
    user_id = os.environ.get("MI_FITNESS_USER_ID") or input("Xiaomi userId: ").strip()
    pass_token = getpass.getpass("Xiaomi passToken (hidden, not persisted): ")
    if not user_id or not pass_token:
        raise AuthenticationError("userId and passToken are required")

    jar = http.cookiejar.CookieJar()
    login_opener = build_xiaomi_opener(jar)
    session = exchange_pass_token(login_opener, jar, user_id, pass_token)
    request = build_read_request(
        args.metric,
        days=args.days,
        utc_offset=args.utc_offset,
    )
    # A separate cookie-less opener prevents account-login cookies from ever
    # reaching the health host. fetch_once supplies only cUserId/serviceToken.
    body = fetch_once(build_xiaomi_opener(), session, request, region=args.region)
    summary = summarize_result(args.metric, request.path, parse_result_envelope(body))
    summary["region"] = args.region
    summary["token_refreshed_in_memory"] = session.token_refreshed
    return summary


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--live",
        action="store_true",
        help="perform one live, allowlisted, read-only GET; otherwise validate a synthetic fixture",
    )
    parser.add_argument(
        "--metric",
        choices=sorted(FITNESS_KEYS | {"workouts"}),
        default="steps",
    )
    parser.add_argument("--days", type=int, default=1)
    parser.add_argument("--region", choices=sorted(KNOWN_REGIONS), default="cn")
    parser.add_argument("--utc-offset", default="+08:00")
    parser.add_argument(
        "--fixture",
        type=Path,
        default=Path(__file__).with_name("fixtures") / "protocol_vectors.json",
        help=argparse.SUPPRESS,
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        summary = run_live(args) if args.live else run_offline_fixture(args.fixture)
    except (PocError, ValueError, KeyError) as exc:
        print(json.dumps({"status": "error", "message": str(exc)}, ensure_ascii=False))
        return 2
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
