"""App Store Connect API helpers for asc_setup.py and asc_previews.py (ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8)."""
import os, time
import jwt, requests

API = "https://api.appstoreconnect.apple.com"
BUNDLE = "com.naimdor.puki"


def token():
    now = int(time.time())
    return jwt.encode({"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
                      os.environ["ASC_KEY_P8"].strip() + "\n", algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"]})


def call(method, path, body=None, params=None):
    r = requests.request(method, API + path, params=params, json=body,
                         headers={"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}, timeout=60)
    if r.status_code >= 400:
        raise RuntimeError(f"{method} {path} -> {r.status_code}: {r.text[:1500]}")
    return r.json() if r.text else {}


def editable_version(app):
    vs = call("GET", f"/v1/apps/{app}/appStoreVersions", params={"filter[platform]": "IOS", "limit": 10})["data"]
    for v in vs:
        if v["attributes"]["appStoreState"] in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED"):
            return v
    raise RuntimeError("no version being prepared: " + ", ".join(f"{v['attributes']['versionString']}={v['attributes']['appStoreState']}" for v in vs))



def app_id():
    apps = call("GET", "/v1/apps", params={"filter[bundleId]": BUNDLE})["data"]
    if not apps:
        raise SystemExit(f"no app with bundle ID {BUNDLE} in App Store Connect")
    return apps[0]["id"]
