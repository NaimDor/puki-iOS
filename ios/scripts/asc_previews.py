#!/usr/bin/env python3
"""Uploads the App Store app previews (fastlane doesn't) through the App Store Connect API.

    python3 ios/scripts/asc_previews.py shots-previews     # shots-previews/<locale>/{iphone,ipad}_preview.mp4

Replaces the previews on the version being prepared, per language: the iPhone 6.9" one (Apple's IPHONE_67 slot)
and the iPad 13" one (IPAD_PRO_3GEN_129). Needs ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8, as asc_setup.py does.
"""
import hashlib, os, sys, time
import requests
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_api import call, app_id, editable_version

TYPES = {"iphone": "IPHONE_67", "ipad": "IPAD_PRO_3GEN_129"}


def preview_set(loc, kind):
    for s in call("GET", f"/v1/appStoreVersionLocalizations/{loc}/appPreviewSets")["data"]:
        if s["attributes"]["previewType"] == kind:
            return s["id"]
    return call("POST", "/v1/appPreviewSets", {"data": {"type": "appPreviewSets", "attributes": {"previewType": kind},
        "relationships": {"appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": loc}}}}})["data"]["id"]


def upload(set_id, path):
    for old in call("GET", f"/v1/appPreviewSets/{set_id}/appPreviews")["data"]:
        call("DELETE", f"/v1/appPreviews/{old['id']}")
    data = open(path, "rb").read()
    p = call("POST", "/v1/appPreviews", {"data": {"type": "appPreviews",
        "attributes": {"fileName": os.path.basename(path), "fileSize": len(data), "mimeType": "video/mp4"},
        "relationships": {"appPreviewSet": {"data": {"type": "appPreviewSets", "id": set_id}}}}})["data"]
    for op in p["attributes"]["uploadOperations"]:
        part = data[op["offset"]:op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders") or []}
        r = requests.request(op["method"], op["url"], data=part, headers=headers, timeout=300)
        r.raise_for_status()
    call("PATCH", f"/v1/appPreviews/{p['id']}", {"data": {"type": "appPreviews", "id": p["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()}}})
    for _ in range(30):  # Apple checks the video; wait to hear whether it took it
        st = call("GET", f"/v1/appPreviews/{p['id']}")["data"]["attributes"].get("assetDeliveryState") or {}
        if st.get("state") in ("COMPLETE", "FAILED"):
            return st
        time.sleep(10)
    return st


def main():
    root = sys.argv[1]
    app = app_id()
    v = editable_version(app)
    locs = {l["attributes"]["locale"]: l["id"] for l in call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreVersionLocalizations")["data"]}
    failed = False
    for locale in sorted(os.listdir(root)):
        if locale not in locs:
            print(f"skip {locale}: no such language on the version ({', '.join(locs)})")
            continue
        for tag, kind in TYPES.items():
            path = os.path.join(root, locale, f"{tag}_preview.mp4")
            if not os.path.exists(path):
                continue
            st = upload(preview_set(locs[locale], kind), path)
            ok = st.get("state") == "COMPLETE"
            failed |= not ok
            print(f"{'OK  ' if ok else 'FAIL'} {locale} {tag}: {st.get('state')} {st.get('errors') or ''}", flush=True)
    sys.exit(1 if failed else 0)


main()
