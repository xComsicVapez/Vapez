#!/usr/bin/env python3
"""Convert a Google Takeout export of Gemini activity into a single Markdown file.

Usage:
    python3 gemini_to_markdown.py <path> [-o gemini_chats.md]

<path> may be:
  - a Takeout .zip archive
  - an extracted Takeout folder
  - a "My Activity" JSON file (MyActivity.json)
  - a "My Activity" HTML file (MyActivity.html)
"""

import argparse
import html
import json
import re
import sys
import zipfile
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path

GEMINI_HEADERS = ("gemini", "bard")
PROMPT_PREFIXES = ("Prompted ", "Prompted")


class HtmlToMarkdown(HTMLParser):
    """Small HTML-to-Markdown converter for the response markup Takeout emits."""

    BLOCK_TAGS = {"p", "div", "br", "tr", "table", "blockquote"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.list_stack = []
        self.in_pre = False
        self.href = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self.out.append("\n\n" + "#" * min(int(tag[1]) + 2, 6) + " ")
        elif tag in ("strong", "b"):
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("_")
        elif tag == "code" and not self.in_pre:
            self.out.append("`")
        elif tag == "pre":
            self.in_pre = True
            self.out.append("\n\n```\n")
        elif tag in ("ul", "ol"):
            self.list_stack.append([tag, 0])
            self.out.append("\n")
        elif tag == "li":
            indent = "  " * max(len(self.list_stack) - 1, 0)
            if self.list_stack and self.list_stack[-1][0] == "ol":
                self.list_stack[-1][1] += 1
                self.out.append(f"\n{indent}{self.list_stack[-1][1]}. ")
            else:
                self.out.append(f"\n{indent}- ")
        elif tag == "a":
            self.href = attrs.get("href")
            self.out.append("[")
        elif tag == "td" or tag == "th":
            self.out.append(" | ")
        elif tag in self.BLOCK_TAGS:
            self.out.append("\n" if tag == "br" else "\n\n")

    def handle_endtag(self, tag):
        if tag in ("strong", "b"):
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("_")
        elif tag == "code" and not self.in_pre:
            self.out.append("`")
        elif tag == "pre":
            self.in_pre = False
            self.out.append("\n```\n\n")
        elif tag in ("ul", "ol"):
            if self.list_stack:
                self.list_stack.pop()
            self.out.append("\n")
        elif tag == "a":
            self.out.append(f"]({self.href})" if self.href else "]")
            self.href = None
        elif tag in ("h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "blockquote"):
            self.out.append("\n\n")

    def handle_data(self, data):
        self.out.append(data if self.in_pre else re.sub(r"\s+", " ", data))

    def markdown(self):
        text = "".join(self.out)
        text = re.sub(r"[ \t]+\n", "\n", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = re.sub(r"\n+```\n\n", "\n```\n\n", text)
        return text.strip()


def html_to_md(fragment):
    parser = HtmlToMarkdown()
    parser.feed(fragment or "")
    parser.close()
    return parser.markdown()


def strip_prompt_prefix(title):
    for prefix in PROMPT_PREFIXES:
        if title.startswith(prefix):
            return title[len(prefix):].strip()
    return title.strip()


def parse_time(value):
    if not value:
        return None
    for candidate in (value.replace("Z", "+00:00"), value):
        try:
            return datetime.fromisoformat(candidate)
        except ValueError:
            pass
    cleaned = re.sub(r"\s+", " ", value.replace("\u202f", " ")).strip()
    cleaned = re.sub(r"\s+[A-Z]{2,5}$", "", cleaned)
    for fmt in ("%b %d, %Y, %I:%M:%S %p", "%d %b %Y, %H:%M:%S", "%b %d, %Y, %H:%M:%S"):
        try:
            return datetime.strptime(cleaned, fmt)
        except ValueError:
            pass
    return None


def is_gemini_entry(entry):
    header = (entry.get("header") or "").lower()
    products = " ".join(entry.get("products") or []).lower()
    return any(name in header or name in products for name in GEMINI_HEADERS)


def entries_from_json(text):
    data = json.loads(text)
    if isinstance(data, dict):
        data = data.get("items") or data.get("activities") or [data]
    entries = []
    for item in data:
        if not isinstance(item, dict) or not is_gemini_entry(item):
            continue
        response = "\n\n".join(
            html_to_md(part.get("html", "")) for part in item.get("safeHtmlItem") or []
        )
        attachments = [f for f in item.get("attachedFiles") or [] if f]
        entries.append({
            "time": parse_time(item.get("time")),
            "raw_time": item.get("time", ""),
            "prompt": strip_prompt_prefix(item.get("title", "")),
            "response": response,
            "attachments": attachments,
        })
    return entries


OUTER_CELL = re.compile(r'<div class="outer-cell[^"]*"[^>]*>', re.I)
CONTENT_CELL = re.compile(
    r'<div class="content-cell[^"]*mdl-typography--body-1"[^>]*>(.*?)</div>', re.S | re.I
)
HEADER_CELL = re.compile(r'<p class="mdl-typography--title"[^>]*>(.*?)</p>', re.S | re.I)
TIME_LINE = re.compile(
    r"([A-Z][a-z]{2} \d{1,2}, \d{4}, \d{1,2}:\d{2}:\d{2}[\s\u202f]*[AP]M(?:\s+[A-Z]{2,5})?"
    r"|\d{1,2} [A-Z][a-z]{2} \d{4}, \d{2}:\d{2}:\d{2}(?:\s+[A-Z]{2,5})?)"
)


def entries_from_html(text):
    entries = []
    blocks = OUTER_CELL.split(text)[1:]
    for block in blocks:
        header_match = HEADER_CELL.search(block)
        header = html.unescape(re.sub(r"<[^>]+>", "", header_match.group(1))) if header_match else ""
        if header and not any(name in header.lower() for name in GEMINI_HEADERS):
            continue
        content_match = CONTENT_CELL.search(block)
        if not content_match:
            continue
        content = content_match.group(1)
        time_match = TIME_LINE.search(html.unescape(content))
        raw_time = time_match.group(1) if time_match else ""
        unescaped = html.unescape(content)
        if time_match:
            prompt_html, _, response_html = unescaped.partition(raw_time)
        else:
            prompt_html, response_html = unescaped, ""
        prompt = html_to_md(prompt_html).replace("\n", " ").strip()
        if not prompt.startswith(PROMPT_PREFIXES) and not header:
            continue
        entries.append({
            "time": parse_time(raw_time),
            "raw_time": raw_time,
            "prompt": strip_prompt_prefix(prompt),
            "response": html_to_md(response_html),
            "attachments": [],
        })
    return entries


def looks_like_activity(name):
    lower = name.lower()
    return lower.endswith((".json", ".html")) and (
        "gemini" in lower or "bard" in lower or "myactivity" in lower.replace(" ", "")
    )


def load_entries(path):
    path = Path(path)
    sources = []
    if path.is_dir():
        sources = [(str(p), p.read_text(encoding="utf-8", errors="replace"))
                   for p in sorted(path.rglob("*")) if p.is_file() and looks_like_activity(str(p))]
    elif zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            sources = [(name, archive.read(name).decode("utf-8", errors="replace"))
                       for name in archive.namelist() if looks_like_activity(name)]
    else:
        sources = [(str(path), path.read_text(encoding="utf-8", errors="replace"))]

    if not sources:
        sys.exit(f"No Gemini / My Activity files found in {path}")

    entries = []
    for name, text in sources:
        stripped = text.lstrip()
        found = entries_from_json(text) if stripped[:1] in "[{" else entries_from_html(text)
        print(f"  {name}: {len(found)} Gemini entries", file=sys.stderr)
        entries.extend(found)

    unique = {}
    for entry in entries:
        unique.setdefault((entry["raw_time"], entry["prompt"]), entry)
    return sorted(unique.values(), key=lambda e: (e["time"] is None, str(e["time"] or "")))


def render_markdown(entries):
    lines = ["# Gemini Chat History", "", f"Total prompts: {len(entries)}", ""]
    current_day = None
    for index, entry in enumerate(entries, 1):
        stamp = entry["time"]
        day = stamp.strftime("%A, %B %d, %Y") if stamp else "Unknown date"
        if day != current_day:
            lines += ["---", "", f"## {day}", ""]
            current_day = day
        when = stamp.strftime("%H:%M:%S") if stamp else entry["raw_time"] or "unknown time"
        lines += [f"### {index}. {when}", "", "**You:**", "", entry["prompt"] or "_(empty)_", ""]
        for attachment in entry["attachments"]:
            lines.append(f"_Attachment: {attachment}_")
        if entry["attachments"]:
            lines.append("")
        lines += ["**Gemini:**", "", entry["response"] or "_(no response saved)_", ""]
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", help="Takeout .zip, folder, MyActivity.json or MyActivity.html")
    parser.add_argument("-o", "--output", default="gemini_chats.md", help="output Markdown file")
    args = parser.parse_args()

    entries = load_entries(args.source)
    Path(args.output).write_text(render_markdown(entries), encoding="utf-8")
    print(f"Wrote {len(entries)} prompts to {args.output}", file=sys.stderr)


if __name__ == "__main__":
    main()
