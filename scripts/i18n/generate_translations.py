#!/usr/bin/env python3
"""Generate Android string resources for all Play Console locales from English master."""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

try:
    from deep_translator import GoogleTranslator
except ImportError:
    print("Install: pip install deep-translator", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
STAGING_ROOT = RES / ".i18n-staging"
MASTER_FILES = ("strings.xml", "strings_categories.xml", "strings_narrative.xml")
DEFAULT_WORKERS = 4

LOCALES: dict[str, str] = {
    "values-af": "af",
    "values-am": "am",
    "values-ar": "ar",
    "values-as": "as",
    "values-az": "az",
    "values-be": "be",
    "values-bg": "bg",
    "values-bn": "bn",
    "values-bs": "bs",
    "values-ca": "ca",
    "values-cs": "cs",
    "values-da": "da",
    "values-de": "de",
    "values-el": "el",
    "values-es": "es",
    "values-es-rUS": "es",
    "values-et": "et",
    "values-eu": "eu",
    "values-fa": "fa",
    "values-fi": "fi",
    "values-fr": "fr",
    "values-fr-rCA": "fr",
    "values-gl": "gl",
    "values-gu": "gu",
    "values-hi": "hi",
    "values-hr": "hr",
    "values-hu": "hu",
    "values-hy": "hy",
    "values-in": "id",
    "values-is": "is",
    "values-it": "it",
    "values-iw": "iw",
    "values-ja": "ja",
    "values-ka": "ka",
    "values-kk": "kk",
    "values-km": "km",
    "values-kn": "kn",
    "values-ko": "ko",
    "values-ky": "ky",
    "values-lo": "lo",
    "values-lt": "lt",
    "values-lv": "lv",
    "values-mk": "mk",
    "values-ml": "ml",
    "values-mn": "mn",
    "values-mr": "mr",
    "values-ms": "ms",
    "values-my": "my",
    "values-nb": "no",
    "values-ne": "ne",
    "values-nl": "nl",
    "values-or": "or",
    "values-pa": "pa",
    "values-pl": "pl",
    "values-pt": "pt",
    "values-pt-rBR": "pt",
    "values-ro": "ro",
    "values-ru": "ru",
    "values-si": "si",
    "values-sk": "sk",
    "values-sl": "sl",
    "values-sq": "sq",
    "values-sr": "sr",
    "values-sv": "sv",
    "values-sw": "sw",
    "values-ta": "ta",
    "values-te": "te",
    "values-th": "th",
    "values-tl": "tl",
    "values-tr": "tr",
    "values-uk": "uk",
    "values-ur": "ur",
    "values-uz": "uz",
    "values-vi": "vi",
    "values-zh-rCN": "zh-CN",
    "values-zh-rHK": "zh-TW",
    "values-zh-rTW": "zh-TW",
    "values-zu": "zu",
}

PLACEHOLDER_RE = re.compile(r"%\d+\$[sd]|%[sd]")


def log(msg: str) -> None:
    print(msg, flush=True)


def parse_strings(path: Path) -> list[tuple[str, str, bool]]:
    tree = ET.parse(path)
    root = tree.getroot()
    items: list[tuple[str, str, bool]] = []
    for node in root.findall("string"):
        name = node.attrib["name"]
        translatable = node.attrib.get("translatable", "true") != "false"
        text = node.text or ""
        items.append((name, text, translatable))
    return items


def load_master() -> dict[str, tuple[str, bool]]:
    merged: dict[str, tuple[str, bool]] = {}
    for filename in MASTER_FILES:
        for name, text, translatable in parse_strings(RES / "values" / filename):
            merged[name] = (text, translatable)
    return merged


def split_by_file() -> dict[str, list[str]]:
    files: dict[str, list[str]] = {f: [] for f in MASTER_FILES}
    for filename in MASTER_FILES:
        for name, _, translatable in parse_strings(RES / "values" / filename):
            if translatable:
                files[filename].append(name)
    return files


def protect_placeholders(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def repl(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"__PH_{len(tokens) - 1}__"

    return PLACEHOLDER_RE.sub(repl, text), tokens


def restore_placeholders(text: str, tokens: list[str]) -> str:
    for i, token in enumerate(tokens):
        for variant in (f"__PH_{i}__", f"__ PH _ {i} __", f"__PH_{i} __"):
            text = text.replace(variant, token)
    return text


def escape_xml(text: str) -> str:
    if "&amp;" in text or "&lt;" in text or "&gt;" in text or "&apos;" in text:
        return (
            text.replace("'", "\\'")
            .replace('"', '\\"')
        )
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', '\\"')
    )


def translate_batch(texts: list[str], target: str) -> list[str]:
    if not texts:
        return []
    translator = GoogleTranslator(source="en", target=target)
    chunk_size = 40
    out: list[str] = []
    for i in range(0, len(texts), chunk_size):
        chunk = texts[i : i + chunk_size]
        for attempt in range(5):
            try:
                out.extend(translator.translate_batch(chunk))
                break
            except Exception:  # noqa: BLE001
                if attempt == 4:
                    for text in chunk:
                        out.append(translator.translate(text))
                else:
                    time.sleep(1.2 * (attempt + 1))
        time.sleep(0.15)
    return out


def write_strings_file(path: Path, entries: list[tuple[str, str]]) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", ""]
    for name, value in entries:
        lines.append(f'    <string name="{name}">{escape_xml(value)}</string>')
    lines.extend(["", "</resources>", ""])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def locale_complete(folder: str) -> bool:
    return all((RES / folder / filename).is_file() for filename in MASTER_FILES)


def sanitize_partial_locales() -> int:
    """Drop interrupted locale folders that only have some XML files."""
    cleaned = 0
    for folder in LOCALES:
        path = RES / folder
        if not path.is_dir():
            continue
        present = [name for name in MASTER_FILES if (path / name).is_file()]
        if present and len(present) < len(MASTER_FILES):
            for name in present:
                (path / name).unlink()
            cleaned += 1
    if STAGING_ROOT.exists():
        shutil.rmtree(STAGING_ROOT, ignore_errors=True)
    return cleaned


def commit_locale_files(folder: str, by_file_entries: dict[str, list[tuple[str, str]]]) -> None:
    staging = STAGING_ROOT / folder
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    try:
        for filename, entries in by_file_entries.items():
            write_strings_file(staging / filename, entries)
        dest = RES / folder
        dest.mkdir(parents=True, exist_ok=True)
        for filename in MASTER_FILES:
            shutil.move(str(staging / filename), str(dest / filename))
    finally:
        if staging.exists():
            shutil.rmtree(staging, ignore_errors=True)
        if STAGING_ROOT.exists() and not any(STAGING_ROOT.iterdir()):
            STAGING_ROOT.rmdir()


def translate_locale(
    folder: str,
    gt_lang: str,
    translatable_names: list[str],
    source_texts: list[str],
    by_file: dict[str, list[str]],
) -> tuple[str, bool, str | None]:
    if locale_complete(folder):
        return folder, True, "skipped"

    protected: list[str] = []
    tokens_list: list[list[str]] = []
    for text in source_texts:
        p, tokens = protect_placeholders(text)
        protected.append(p)
        tokens_list.append(tokens)

    translated = translate_batch(protected, gt_lang)
    if len(translated) != len(source_texts):
        raise RuntimeError(f"translation count mismatch: {len(translated)} != {len(source_texts)}")

    restored = [
        restore_placeholders(t, tok) for t, tok in zip(translated, tokens_list, strict=True)
    ]
    lookup = dict(zip(translatable_names, restored, strict=True))

    entries_by_file = {
        filename: [(name, lookup[name]) for name in names]
        for filename, names in by_file.items()
    }
    commit_locale_files(folder, entries_by_file)
    return folder, True, None


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Android translations from values/*.xml")
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS, help="parallel locale workers")
    args = parser.parse_args()

    cleaned = sanitize_partial_locales()
    if cleaned:
        log(f"Cleaned {cleaned} partial locale folder(s) from interrupted run")

    master = load_master()
    by_file = split_by_file()
    translatable_names = [n for n, (_, t) in master.items() if t]
    source_texts = [master[n][0] for n in translatable_names]

    pending = {f: lang for f, lang in LOCALES.items() if not locale_complete(f)}
    log(f"Master: {len(translatable_names)} strings | locales {len(LOCALES)} | pending {len(pending)}")

    if not pending:
        log("All locales already generated.")
        return

    failures: list[str] = []
    workers = max(1, min(args.workers, len(pending)))
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(
                translate_locale,
                folder,
                gt_lang,
                translatable_names,
                source_texts,
                by_file,
            ): folder
            for folder, gt_lang in pending.items()
        }
        done = 0
        for future in as_completed(futures):
            folder = futures[future]
            done += 1
            try:
                name, _, note = future.result()
                if note == "skipped":
                    log(f"[{done}/{len(pending)}] {name} skipped")
                else:
                    log(f"[{done}/{len(pending)}] {name} ok")
            except Exception as exc:  # noqa: BLE001
                failures.append(f"{folder}: {exc}")
                log(f"[{done}/{len(pending)}] {folder} FAILED: {exc}")

    if STAGING_ROOT.exists():
        shutil.rmtree(STAGING_ROOT, ignore_errors=True)

    if failures:
        log(f"Failures ({len(failures)}):")
        for line in failures:
            log(f"  {line}")
        sys.exit(1)

    log("Done.")


if __name__ == "__main__":
    main()
