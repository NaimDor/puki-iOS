#!/usr/bin/env python3
"""Uploads the product page header and search results art (ios/fastlane/creative) through the App Asset Library.

    python3 ios/scripts/asc_creative.py ios/fastlane/creative

Each image goes into the app's asset library once (category CREATIVE_ASSETS), then gets a placement on every
language of the version being prepared, replacing the placement of that type that was there. The placement types
and device groups come from Apple's reference data, which this prints, so a name Apple changes shows up in the log.
Needs ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8, like asc_previews.py. Never submits anything.
"""
import json, os, re, sys, time
import requests
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_api import call, app_id, editable_version

# file -> what it is for (a word to find the placement type by, in Apple's reference data)
ART = {"puki_header_3840x1646.png": "HEADER", "puki_search_3840x2560.png": "SEARCH"}


def walk(x):
    yield x
    if isinstance(x, dict):
        for v in x.values():
            yield from walk(v)
    elif isinstance(x, list):
        for v in x:
            yield from walk(v)


def strings(x):
    return [s for s in walk(x) if isinstance(s, str)]


def ref_data():
    out, path, params = [], "/v1/appAssetLibraryRefData", None
    while path:
        r = call("GET", path, params=params)
        out += r.get("data", [])
        nxt = (r.get("links") or {}).get("next")
        path, params = (nxt.replace("https://api.appstoreconnect.apple.com", ""), None) if nxt else (None, None)
    return out


def placement_choices(ref, word):
    """The placement types named after `word`, each with the device groups that come with it in the reference data."""
    ident = re.compile(r"^[A-Z0-9]+(_[A-Z0-9]+)+$")
    types = sorted({s for s in strings(ref) if ident.match(s) and word in s and ("ASSET" in s or "PLACEMENT" in s or "HEADER" in s)})
    choices = []
    for t in types:
        groups = set()
        for d in walk(ref):
            if isinstance(d, dict) and t in strings(d) and len(json.dumps(d)) < 20000:
                groups |= {s for s in strings(d) if ident.match(s) and s != t and s.endswith(("_PROFILE", "_GROUP", "_CLASS", "UNIVERSAL", "_ALL"))}
        choices.append((t, sorted(groups)))
    return choices


def upload(library, path):
    data = open(path, "rb").read()
    img = call("POST", "/v1/appAssetLibraryImages", {"data": {"type": "appAssetLibraryImages",
        "attributes": {"fileName": os.path.basename(path), "fileSize": len(data), "category": "CREATIVE_ASSETS",
                       "referenceName": os.path.splitext(os.path.basename(path))[0]},
        "relationships": {"assetLibrary": {"data": {"type": "appAssetLibraries", "id": library}}}}})["data"]
    for op in img["attributes"]["uploadOperations"]:
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders") or []}
        requests.request(op["method"], op["url"], data=data[op["offset"]:op["offset"] + op["length"]], headers=headers, timeout=300).raise_for_status()
    call("PATCH", f"/v1/appAssetLibraryImages/{img['id']}", {"data": {"type": "appAssetLibraryImages", "id": img["id"], "attributes": {"uploaded": True}}})
    for _ in range(60):
        a = call("GET", f"/v1/appAssetLibraryImages/{img['id']}")["data"]["attributes"]
        if a["state"] in ("PREPARE_FOR_SUBMISSION", "FAILED"):
            break
        time.sleep(5)
    print(f"  {os.path.basename(path)}: {a['state']} spec {a.get('specId')} {a.get('stateDetails') or ''}", flush=True)
    return img["id"], a["state"] == "PREPARE_FOR_SUBMISSION"


def place(image, loc, choices):
    """Tries each type and group from the reference data until App Store Connect takes one."""
    errors = []
    for ptype, groups in choices:
        for old in call("GET", f"/v1/appStoreVersionLocalizations/{loc}/placements", params={"filter[placementType]": ptype})["data"]:
            call("DELETE", f"/v1/appAssetLibraryPlacements/{old['id']}")
        for group in groups or [None]:
            attrs = {"placementType": ptype}
            if group:
                attrs["placementGroup"] = group
            try:
                call("POST", "/v1/appAssetLibraryPlacements", {"data": {"type": "appAssetLibraryPlacements", "attributes": attrs,
                    "relationships": {"image": {"data": {"type": "appAssetLibraryImages", "id": image}},
                                      "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": loc}}}}})
                return f"{ptype} / {group}"
            except RuntimeError as e:
                errors.append(f"{ptype}/{group}: {str(e)[:300]}")
    raise RuntimeError("no placement took it:\n    " + "\n    ".join(errors))


def main():
    root = sys.argv[1]
    app = app_id()
    v = editable_version(app)
    print(f"version {v['attributes']['versionString']} ({v['attributes']['appStoreState']})")
    library = call("GET", f"/v1/apps/{app}/assetLibrary")["data"]["id"]
    ref = ref_data()
    print("reference data:", json.dumps(ref)[:6000], flush=True)
    locs = {l["attributes"]["locale"]: l["id"] for l in call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreVersionLocalizations")["data"]}
    failed = False
    for name, word in ART.items():
        choices = placement_choices(ref, word)
        print(f"{name}: placement types {choices}", flush=True)
        if not choices:
            print(f"FAIL {name}: no placement type with {word} in the reference data")
            failed = True
            continue
        image, ok = upload(library, os.path.join(root, name))
        if not ok:
            failed = True
            continue
        for locale, loc in sorted(locs.items()):
            try:
                print(f"OK   {locale} {name}: {place(image, loc, choices)}", flush=True)
            except RuntimeError as e:
                failed = True
                print(f"FAIL {locale} {name}: {e}", flush=True)
    sys.exit(1 if failed else 0)


main()
