"""Check the real Compose navigation wiring; full compilation/rendering is separate."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    android = Path(__file__).resolve().parents[3]
    source = android / "app/src/main/java/dev/betterendfield/next"
    activity = (source / "MainActivity.kt").read_text(encoding="utf-8")
    shell = (source / "SettingsShell.kt").read_text(encoding="utf-8")
    state = (source / "SettingsState.kt").read_text(encoding="utf-8")
    tools = (source / "ToolPages.kt").read_text(encoding="utf-8")
    manifest = ET.parse(android / "app/src/main/AndroidManifest.xml")
    checks = []

    def check(condition, description):
        if not condition:
            raise AssertionError(description)
        checks.append(description)

    pages = {"HOME": "HomePage", "EXPERIENCE": "ExperiencePage",
             "CHARACTERS": "CharacterPage", "TOOLS": "ToolsPage",
             "FIRST_PERSON": "FirstPersonPage", "CAMERA_MOTION": "CameraMotionPage",
             "MMD": "MmdPage", "APPEARANCE": "AppearancePage", "LOG": "LogPage", "ABOUT": "AboutPage",
             "ADMINISTRATOR_APPEARANCE": "AdministratorAppearancePage"}
    for page, component in pages.items():
        check(re.search(r"const val " + page + r" = [0-9]+", state) is not None
              and re.search(r"SettingsPage\." + page + r"\s*->\s*" + component + r"\(", shell) is not None,
              page + " is declared by SettingsPage and rendered by SettingsShell")
    for request, page in {"custom_model": "APPEARANCE", "first_person": "FIRST_PERSON",
                          "tools": "TOOLS", "enhancement": "EXPERIENCE", "diagnostics": "TOOLS"}.items():
        check('"' + request + '" -> SettingsPage.' + page in activity,
              request + " intent routes to " + page)
    check("settings.restorePage(initialPage)" in activity and "savedInstanceState?.getInt(KEY_PAGE" in activity
          and "outState.putInt(KEY_PAGE, settings.page)" in activity,
          "saved page is restored and current page is saved")
    check("SettingsPage.parentOf(state.page)" in shell, "tab selection uses the page owner")
    ns = "{http://schemas.android.com/apk/res/android}"
    activities = {node.get(ns + "name") for node in manifest.iter("activity")}
    check(".MainActivity" in activities and ".BemInstallActivity" in activities,
          "Compose settings and package manager activities are registered")
    check("ThirdPartyModulesActivity::class.java" in tools and ".ThirdPartyModulesActivity" in activities,
          "retained local module manager is wired and registered")
    check("MediaScreenActivity::class.java" in tools and ".MediaScreenActivity" in activities,
          "upstream media screen is wired and registered")
    mmd = (source / "MmdPage.kt").read_text(encoding="utf-8")
    check("MmdImportActivity::class.java" in mmd and ".MmdImportActivity" in activities,
          "MMD reviewed import is wired and registered")
    about = (source / "AboutPage.kt").read_text(encoding="utf-8")
    project = (source / "ProjectLinks.kt").read_text(encoding="utf-8")
    check("ProjectLinks.REPOSITORY" in about and "ProjectLinks.LICENSE" in about
          and "https://github.com/NukumizuKazuhiko/better-endfield-fixed" in project,
          "project and license actions use the fork identity")
    check(not (source / "SponsorDialog.java").exists()
          and not (android / "app/src/main/res/raw/sponsor_wechat.png").exists()
          and all(key not in about for key in ("next_bilibili", "next_xiaoheihe", "next_qq_group", "next_discord", "SponsorDialog")),
          "author personal links and sponsorship resources are removed")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    files = [source / name for name in ("MainActivity.kt", "SettingsShell.kt", "SettingsState.kt", "ToolPages.kt")]
    report = {"kind": "static-source-wiring", "checks": checks,
              "source_sha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
              "limitations": ["Does not execute Android navigation or render Compose", "Does not validate browser launches"]}
    (output / "navigation-wiring.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"PASS Compose navigation source wiring: {len(checks)} checks (not runtime/UI acceptance)")


if __name__ == "__main__":
    main()
