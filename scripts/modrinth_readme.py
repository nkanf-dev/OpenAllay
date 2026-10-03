"""Prepare the repository's inline README links/images for Modrinth.

This preserves source bytes outside URL destinations. It supports the simple
inline forms used here, not reference links or arbitrarily nested Markdown.
"""
import re

RAW = "https://raw.githubusercontent.com/nkanf-dev/OpenAllay/main/"
BLOB = "https://github.com/nkanf-dev/OpenAllay/blob/main/"
DOCUMENTS = ("README.zh-CN.md", "docs/development.md", "LICENSE")
TOKENS = re.compile(
    r"(?P<literal>"
    r"(?m:^ {0,3}(?P<fence>`{3,}|~{3,})[^\n]*\n[\s\S]*?^ {0,3}(?P=fence)[ \t]*(?:\n|$))"
    r"|(?P<ticks>`+)[^\n]*?(?P=ticks)|<!--[\s\S]*?-->|\\[^\n])"
    r"|(?P<html><(?P<tag>[A-Za-z][\w:-]*)\b(?:[^<>\"']|\"[^\"]*\"|'[^']*')*>)"
    r"|(?P<image>!)?\[(?:\\.|[^\[\]\\\n])*\]\(\s*"
    r"(?P<url><[^<>\n]*>|(?:\\.|[^\s<>\\()]|\([^()\n]*\))+)"
    r"(?:\s+(?:\"(?:\\.|[^\"\\\n])*\"|'(?:\\.|[^'\\\n])*'|\([^()\n]*\)))?\s*\)"
)
ATTRIBUTES = re.compile(r"""([^\s=/>]+)(?:\s*=\s*("[^"]*"|'[^']*'|[^\s>]+))?""")


def render_readme(body):
    """Rewrite only local media image URLs and the three established doc links."""
    def attribute(match):
        if match[1].lower() != "src" or match[2] is None:
            return match[0]
        value = match[2]
        quoted = value[:1] in ("'", '"')
        destination = value[1:-1] if quoted else value
        if not destination.startswith("docs/media/"):
            return match[0]
        start = match.start(2) - match.start() + int(quoted)
        return match[0][:start] + RAW + match[0][start:]

    def token(match):
        if match["literal"] is not None:
            return match[0]
        if match["html"] is not None:
            return ATTRIBUTES.sub(attribute, match[0]) if match["tag"].lower() == "img" else match[0]
        url = match["url"]
        angled = url.startswith("<")
        destination = url[1:-1] if angled else url
        prefix = None
        if match["image"] and destination.startswith("docs/media/"):
            prefix = RAW
        elif not match["image"] and any(destination == path or destination.startswith((path + "#", path + "?"))
                                        for path in DOCUMENTS):
            prefix = BLOB
        if prefix is None:
            return match[0]
        start = match.start("url") - match.start() + int(angled)
        return match[0][:start] + prefix + match[0][start:]

    return TOKENS.sub(token, body)
