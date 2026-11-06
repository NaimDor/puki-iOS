#!/usr/bin/env python3
"""Uploads the product page header and search results art (ios/fastlane/creative) through the App Asset Library.

    python3 ios/scripts/asc_creative.py ios/fastlane/creative

Each image goes into the app's asset library once (category CREATIVE_ASSETS), then gets a placement on every
language of the version being prepared, replacing the placement of that type that was there. An image already in
the library (same name and size) is reused, so running this again doesn't pile up copies.
Needs ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8, like asc_previews.py. Never submits anything.
"""
import os, sys, time
import requests
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_api import call, app_id, editable_version

# (both slots take one image, in the group DEFAULT_PROFILE, per Apple's reference data)
ART = {"puki_header_3840x1646.png": "PRODUCT_PAGE_HEADER_ASSET", "puki_search_3840x2560.png": "APP_STORE_SEARCH_RESULTS_ASSET"}
GROUP = "DEFAULT_PROFILE"


def wait(image):
    for _ in range(120):  # processing can take several minutes
        a = call("GET", f"/v1/appAssetLibraryImages/{image}")["data"]["attributes"]
        if a["state"] not in ("AWAITING_UPLOAD", "UPLOAD_COMPLETE"):
            return a
        time.sleep(10)
    return a


def upload(library, path):
    """The image in the library: the one uploaded before, if it's there and not failed, or a new upload."""
    ref = os.path.splitext(os.path.basename(path))[0]
    data = open(path, "rb").read()
    for old in call("GET", f"/v1/appAssetLibraries/{library}/images", params={"filter[referenceName]": ref, "sort": "-createdDate"})["data"]:
        a = old["attributes"]
        if a.get("fileSize") == len(data) and a["state"] not in ("FAILED", "AWAITING_UPLOAD", "ARCHIVED", "REJECTED"):
            a = wait(old["id"])
            print(f"  {ref}: already in the library, {a['state']} spec {a.get('specId')} {a.get('stateDetails') or ''}", flush=True)
            return old["id"], a["state"] == "PREPARE_FOR_SUBMISSION" or a["state"] in ("READY_FOR_REVIEW", "WAITING_FOR_REVIEW", "ACCEPTED", "APPROVED")
    img = call("POST", "/v1/appAssetLibraryImages", {"data": {"type": "appAssetLibraryImages",
        "attributes": {"fileName": os.path.basename(path), "fileSize": len(data), "category": "CREATIVE_ASSETS", "referenceName": ref},
        "relationships": {"assetLibrary": {"data": {"type": "appAssetLibraries", "id": library}}}}})["data"]
    for op in img["attributes"]["uploadOperations"]:
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders") or []}
        requests.request(op["method"], op["url"], data=data[op["offset"]:op["offset"] + op["length"]], headers=headers, timeout=300).raise_for_status()
    call("PATCH", f"/v1/appAssetLibraryImages/{img['id']}", {"data": {"type": "appAssetLibraryImages", "id": img["id"], "attributes": {"uploaded": True}}})
    a = wait(img["id"])
    print(f"  {ref}: uploaded, {a['state']} spec {a.get('specId')} {a.get('stateDetails') or ''}", flush=True)
    return img["id"], a["state"] == "PREPARE_FOR_SUBMISSION"


def place(image, loc, ptype):
    """The image in its slot on one language, in place of whatever was there."""
    for old in call("GET", f"/v1/appStoreVersionLocalizations/{loc}/placements", params={"filter[placementType]": ptype})["data"]:
        call("DELETE", f"/v1/appAssetLibraryPlacements/{old['id']}")
    p = call("POST", "/v1/appAssetLibraryPlacements", {"data": {"type": "appAssetLibraryPlacements",
        "attributes": {"placementType": ptype, "placementGroup": GROUP},
        "relationships": {"image": {"data": {"type": "appAssetLibraryImages", "id": image}},
                          "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": loc}}}}})["data"]
    return p["attributes"]["state"]


def main():
    root = sys.argv[1]
    app = app_id()
    v = editable_version(app)
    print(f"version {v['attributes']['versionString']} ({v['attributes']['appStoreState']})")
    library = call("GET", f"/v1/apps/{app}/assetLibrary")["data"]["id"]
    locs = {l["attributes"]["locale"]: l["id"] for l in call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreVersionLocalizations")["data"]}
    failed = False
    for name, ptype in ART.items():
        image, ok = upload(library, os.path.join(root, name))
        if not ok:
            failed = True
            print(f"FAIL {name}: Apple didn't finish processing it (run this again later)")
            continue
        for locale, loc in sorted(locs.items()):
            try:
                print(f"OK   {locale} {ptype}: {place(image, loc, ptype)}", flush=True)
            except RuntimeError as e:
                failed = True
                print(f"FAIL {locale} {ptype}: {e}", flush=True)
    sys.exit(1 if failed else 0)


main()
