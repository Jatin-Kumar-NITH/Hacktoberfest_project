"""Firebase Cloud Messaging: tells the Android app that something new was posted, so it syncs
right away instead of waiting for its hourly check.

A push carries no content, only what changed ("campus", "news" or "chat"). The app then
fetches the details over its own signed-in connection, so nothing private goes through Firebase
and a phone that isn't signed in learns nothing from it.

Setup (both optional; without them the app falls back to its hourly check):
  FIREBASE_GOOGLE_SERVICES  - the google-services.json file Firebase gives you for the Android app
  FIREBASE_SERVICE_ACCOUNT  - a service account key (JSON) from the same Firebase project
Either may be pasted as plain JSON or base64.
"""
import base64
import json
import logging
import os
import threading

ANDROID_PACKAGE = "app.vercel.campusmate_nith"
SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

_lock = threading.Lock()
_creds = None


def _env_json(name):
    raw = os.getenv(name, "").strip()
    if not raw:
        return None
    try:
        return json.loads(raw if raw.startswith("{") else base64.b64decode(raw))
    except (ValueError, TypeError):
        logging.warning("%s is not valid JSON or base64 JSON", name)
        return None


def app_config():
    """What the Android app needs to start Firebase (all public values), or None if not set up."""
    gs = _env_json("FIREBASE_GOOGLE_SERVICES")
    if not gs:
        return None
    try:
        project = gs["project_info"]
        client = next((c for c in gs["client"]
                       if c["client_info"]["android_client_info"]["package_name"] == ANDROID_PACKAGE), gs["client"][0])
        return {
            "project_id": project["project_id"],
            "sender_id": project["project_number"],
            "app_id": client["client_info"]["mobilesdk_app_id"],
            "api_key": client["api_key"][0]["current_key"],
        }
    except (KeyError, IndexError, TypeError):
        logging.warning("FIREBASE_GOOGLE_SERVICES doesn't look like a google-services.json file")
        return None


def _token():
    global _creds
    info = _env_json("FIREBASE_SERVICE_ACCOUNT")
    if not info:
        return None, None
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
    with _lock:
        if _creds is None:
            _creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
        if not _creds.valid:
            _creds.refresh(Request())
        return _creds.token, info["project_id"]


def send(topic, **data):
    """Wake every phone subscribed to [topic]. Never raises: a failed push only means the
    app picks the change up on its next regular check."""
    try:
        token, project = _token()
        if not token:
            return
        import requests
        res = requests.post(
            f"https://fcm.googleapis.com/v1/projects/{project}/messages:send",
            headers={"Authorization": f"Bearer {token}"},
            json={"message": {"topic": topic, "data": {k: str(v) for k, v in data.items()},
                              "android": {"priority": "high", "ttl": "86400s"}}},
            timeout=4,
        )
        if res.status_code >= 300:
            logging.warning("FCM push to %s failed: %s %s", topic, res.status_code, res.text[:200])
    except Exception:  # noqa: BLE001 - a push must never break the request that triggered it
        logging.exception("FCM push to %s failed", topic)
