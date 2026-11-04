#!/usr/bin/env python3
"""Fills App Store Connect's forms for Puki through the App Store Connect API (never submits for review).

Needs ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8 in the environment (the TestFlight workflow's secrets).
Sets: content rights, the Kids category (5 and under) with Education, the age rating, a free price, every
territory, the App Review contact and notes, and the newest valid build on the version being prepared.
The App Privacy answers ("Data Not Collected") aren't in Apple's API: those stay a form in the website.
Each step reports what it did and carries on if one fails, so one run shows everything left to do.
"""
import json, os, sys, time
import jwt, requests

BUNDLE = "com.naimdor.puki"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REVIEW = os.path.join(ROOT, "fastlane/metadata/review_information")
API = "https://api.appstoreconnect.apple.com"
problems = []


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


def step(name):
    def wrap(f):
        def run(*a):
            try:
                f(*a)
                print(f"OK   {name}", flush=True)
            except Exception as e:  # report and carry on
                problems.append(name)
                print(f"FAIL {name}: {e}", flush=True)
        return run
    return wrap


def read(name):
    p = os.path.join(REVIEW, name)
    return open(p, encoding="utf-8").read().strip() if os.path.exists(p) else ""


@step("content rights (uses third-party content, with the rights)")
def content_rights(app):
    call("PATCH", f"/v1/apps/{app}", {"data": {"type": "apps", "id": app,
                                                "attributes": {"contentRightsDeclaration": "USES_THIRD_PARTY_CONTENT"}}})


def editable_app_info(app):
    infos = call("GET", f"/v1/apps/{app}/appInfos")["data"]
    for i in infos:
        if i["attributes"].get("appStoreState") not in ("READY_FOR_SALE", "REPLACED_WITH_NEW_INFO", "REMOVED_FROM_SALE"):
            return i
    return infos[0]


@step("categories (Education; Games, Family)")
def categories(app):
    # Apple has no separate Kids category any more: "made for kids" is the age rating's age band (below)
    info = editable_app_info(app)
    call("PATCH", f"/v1/appInfos/{info['id']}", {"data": {"type": "appInfos", "id": info["id"], "relationships": {
        "primaryCategory": {"data": {"type": "appCategories", "id": "EDUCATION"}},
        "secondaryCategory": {"data": {"type": "appCategories", "id": "GAMES"}},
        "secondarySubcategoryOne": {"data": {"type": "appCategories", "id": "GAMES_FAMILY"}}}}})


# Puki has none of these: an enum answer is NONE, a yes/no answer is no (a parental control, the time limit, is yes)
AGE_YES = {"parentalControls"}
AGE_ENUM_NONE = {"alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "gunsOrOtherWeapons",
                 "medicalOrTreatmentInformation", "profanityOrCrudeHumor", "sexualContentGraphicAndNudity",
                 "sexualContentOrNudity", "horrorOrFearThemes", "matureOrSuggestiveThemes", "violenceCartoonOrFantasy",
                 "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic"}


@step("age rating (nothing objectionable; Kids, 5 and under)")
def age_rating(app):
    info = editable_app_info(app)
    decl = call("GET", f"/v1/appInfos/{info['id']}/ageRatingDeclaration")["data"]
    attrs = {}
    for k, v in decl["attributes"].items():
        if "Override" in k or "Classification" in k or k.endswith("Url"):
            continue  # (Apple's own overrides and other countries' ratings: not ours to set)
        if k == "kidsAgeBand":
            attrs[k] = "FIVE_AND_UNDER"
        elif k in AGE_ENUM_NONE:
            attrs[k] = "NONE"
        elif k in AGE_YES:
            attrs[k] = True
        elif isinstance(v, bool) or k in ("unrestrictedWebAccess", "gambling", "lootBox", "advertising", "messagingAndChat",
                                          "userGeneratedContent", "ageAssurance", "healthOrWellnessTopics"):
            attrs[k] = False
        elif isinstance(v, str) and v in ("NONE", "INFREQUENT_OR_MILD", "FREQUENT_OR_INTENSE"):
            attrs[k] = "NONE"
    skipped = sorted(set(decl["attributes"]) - set(attrs))
    if skipped:
        print("     left as they are:", ", ".join(f"{k}={decl['attributes'][k]!r}" for k in skipped))
    print("     set:", json.dumps(attrs))
    call("PATCH", f"/v1/ageRatingDeclarations/{decl['id']}", {"data": {"type": "ageRatingDeclarations", "id": decl["id"],
                                                                       "attributes": attrs}})


@step("price (free)")
def price(app):
    points = call("GET", f"/v1/apps/{app}/appPricePoints", params={"filter[territory]": "USA", "limit": 200})["data"]
    free = next(p for p in points if float(p["attributes"]["customerPrice"]) == 0)
    call("POST", "/v1/appPriceSchedules", {
        "data": {"type": "appPriceSchedules", "relationships": {
            "app": {"data": {"type": "apps", "id": app}},
            "baseTerritory": {"data": {"type": "territories", "id": "USA"}},
            "manualPrices": {"data": [{"type": "appPrices", "id": "${free}"}]}}},
        "included": [{"type": "appPrices", "id": "${free}", "attributes": {"startDate": None},
                      "relationships": {"appPricePoint": {"data": {"type": "appPricePoints", "id": free["id"]}}}}]})


@step("availability (every territory, and new ones)")
def availability(app):
    territories, url, params = [], "/v1/territories", {"limit": 200}
    territories = [t["id"] for t in call("GET", url, params=params)["data"]]
    refs = [{"type": "territoryAvailabilities", "id": f"${{t{i}}}"} for i in range(len(territories))]
    included = [{"type": "territoryAvailabilities", "id": f"${{t{i}}}", "attributes": {"available": True},
                 "relationships": {"territory": {"data": {"type": "territories", "id": t}}}} for i, t in enumerate(territories)]
    call("POST", "/v2/appAvailabilities", {"data": {"type": "appAvailabilities", "attributes": {"availableInNewTerritories": True},
                                                    "relationships": {"app": {"data": {"type": "apps", "id": app}},
                                                                      "territoryAvailabilities": {"data": refs}}},
                                           "included": included})
    print(f"     {len(territories)} territories")


def editable_version(app):
    vs = call("GET", f"/v1/apps/{app}/appStoreVersions", params={"filter[platform]": "IOS", "limit": 10})["data"]
    for v in vs:
        if v["attributes"]["appStoreState"] in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED"):
            return v
    raise RuntimeError("no version being prepared: " + ", ".join(f"{v['attributes']['versionString']}={v['attributes']['appStoreState']}" for v in vs))


@step("App Review contact and notes")
def review(app):
    v = editable_version(app)
    attrs = {"contactEmail": read("email_address.txt"), "notes": read("notes.txt"), "demoAccountRequired": False}
    for key, f in (("contactFirstName", "first_name.txt"), ("contactLastName", "last_name.txt"), ("contactPhone", "phone_number.txt")):
        if read(f):
            attrs[key] = read(f)
    missing = [f for f in ("first_name.txt", "last_name.txt", "phone_number.txt") if not read(f)]
    try:
        d = call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreReviewDetail")["data"]
    except RuntimeError:
        d = None
    if d:
        call("PATCH", f"/v1/appStoreReviewDetails/{d['id']}", {"data": {"type": "appStoreReviewDetails", "id": d["id"], "attributes": attrs}})
    else:
        call("POST", "/v1/appStoreReviewDetails", {"data": {"type": "appStoreReviewDetails", "attributes": attrs, "relationships": {
            "appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    if missing:
        raise RuntimeError("still needed for review: " + ", ".join(missing))


@step("build on the version")
def build(app):
    v = editable_version(app)
    ver = v["attributes"]["versionString"]
    bs = call("GET", "/v1/builds", params={"filter[app]": app, "filter[preReleaseVersion.version]": ver,
                                          "filter[processingState]": "VALID", "sort": "-uploadedDate", "limit": 5})["data"]
    if not bs:
        raise RuntimeError(f"no processed build of {ver} yet")
    b = bs[0]
    call("PATCH", f"/v1/appStoreVersions/{v['id']}/relationships/build", {"data": {"type": "builds", "id": b["id"]}})
    print(f"     version {ver}: build {b['attributes']['version']}")


def main():
    apps = call("GET", "/v1/apps", params={"filter[bundleId]": BUNDLE})["data"]
    if not apps:
        sys.exit(f"no app with bundle ID {BUNDLE} in App Store Connect")
    app = apps[0]["id"]
    print(f"app: {apps[0]['attributes']['name']} ({app})", flush=True)
    for f in (content_rights, categories, age_rating, price, availability, review, build):
        f(app)
    print()
    print("Still to do in the website: App Privacy -> Get Started -> 'No, we do not collect data' -> Save -> Publish.")
    if problems:
        print("Needs a look:", "; ".join(problems))


main()
