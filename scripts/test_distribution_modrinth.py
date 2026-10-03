"""No-network regressions for the exact Modrinth publication payload code."""
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
RAW = "https://raw.githubusercontent.com/nkanf-dev/OpenAllay/main/"
BLOB = "https://github.com/nkanf-dev/OpenAllay/blob/main/"


def production_payloads(readme):
    """Execute both production heredocs, without running curl or publication."""
    script = (ROOT / "scripts/publish-modrinth.sh").read_text(encoding="utf-8")
    blocks = re.findall(
        r"""python3 - "\$repository" "\$work/(project(?:-update)?\.json)" <<'PY'\n(.*?)\nPY""",
        script, re.DOTALL,
    )
    if {name for name, _ in blocks} != {"project.json", "project-update.json"} or len(blocks) != 2:
        raise AssertionError("Both production create/PATCH payload paths must be tested")
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        (root / "README.md").write_text(readme, encoding="utf-8")
        # Production imports the reusable helper from the repository's scripts.
        (root / "scripts").symlink_to(ROOT / "scripts", target_is_directory=True)
        payloads = {}
        for filename, code in blocks:
            output = root / filename
            result = subprocess.run(
                [sys.executable, "-B", "-", str(root), str(output)], input=code,
                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
            )
            if result.returncode:
                raise AssertionError(result.stderr)
            payloads[filename] = json.loads(output.read_text(encoding="utf-8"))
        return payloads


class ModrinthPayloadTest(unittest.TestCase):
    def test_both_production_paths_rewrite_banner_and_screenshots(self):
        readme = (
            '<img src="docs/media/openallay-banner.png" alt="Banner">\n'
            '![Conversation](docs/media/screenshots/openallay-chat.png)\n'
            '![HUD](docs/media/screenshots/openallay-hud.png "Gameplay HUD")\n'
            '[中文](README.zh-CN.md) [Development](docs/development.md) [MIT](LICENSE)\n'
        )
        expected = readme.replace('"docs/media/', '"' + RAW + 'docs/media/').replace(
            '](docs/media/', '](' + RAW + 'docs/media/',
        )
        for document in ("README.zh-CN.md", "docs/development.md", "LICENSE"):
            expected = expected.replace('](' + document + ')', '](' + BLOB + document + ')')
        payloads = production_payloads(readme)
        for name, payload in payloads.items():
            with self.subTest(path=name):
                self.assertEqual(payload["body"], expected)
        self.assertEqual(payloads["project.json"]["body"], payloads["project-update.json"]["body"])
        self.assertEqual(payloads["project.json"]["project_type"], "mod")
        self.assertNotIn("project_type", payloads["project-update.json"])


def helper():
    spec = spec_from_file_location("modrinth_readme", ROOT / "scripts/modrinth_readme.py")
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


readme_helper = helper()


class ModrinthReadmeTest(unittest.TestCase):
    def test_markdown_image_punctuation_and_titles_are_preserved(self):
        for image in (
            '![HUD, v0.4.0!](docs/media/screenshots/hud.png "HUD: docs/media/title.png")',
            r"![HUD](docs/media/screenshots/hud(2).png 'Player\'s HUD')",
            r'![HUD \] with punctuation](docs/media/screenshots/hud\(2\).png "Say \"HUD\"")',
            '![HUD](<docs/media/screenshots/hud 2.png> "Gameplay HUD")',
            '![HUD](docs/media/screenshots/hud.png?raw=1#view)',
        ):
            with self.subTest(image=image):
                marker = '](<' if '](<' in image else ']('
                expected = image.replace(marker + 'docs/media/', marker + RAW + 'docs/media/', 1)
                self.assertEqual(readme_helper.render_readme(image), expected)

    def test_html_changes_only_img_src_and_keeps_quotes_and_attributes(self):
        for image in (
            '<img src="docs/media/banner.png" alt="docs/media/alt.png" title="Screenshot">',
            "<IMG alt='src=docs/media/alt.png' SRC = 'docs/media/hud(2).png' />",
            '<img data-src="docs/media/lazy.png" src=docs/media/hud.png width="800">',
        ):
            with self.subTest(image=image):
                expected = image.replace('docs/media/banner.png', RAW + 'docs/media/banner.png').replace(
                    'docs/media/hud(2).png', RAW + 'docs/media/hud(2).png',
                ).replace('src=docs/media/hud.png', 'src=' + RAW + 'docs/media/hud.png')
                self.assertEqual(readme_helper.render_readme(image), expected)

    def test_external_nonimage_plain_text_and_code_are_unchanged(self):
        body = (
            '![External](https://example.invalid/docs/media/a.png "docs/media/title.png")\n'
            '![Raw](https://raw.githubusercontent.com/nkanf-dev/OpenAllay/main/docs/media/a.png)\n'
            '![Remote](//example.invalid/docs/media/a.png)\n'
            '![Other](assets/docs/media/a.png) ![Absolute](/docs/media/a.png)\n'
            '[Screenshot](docs/media/a.png) [Release](docs/releases/0.4.0.md) [Local](other.md)\n'
            '[External](https://example.invalid/README.zh-CN.md) [Anchor](#docs/media/a.png)\n'
            '<a href="docs/media/a.png">Screenshot</a> <source src="docs/media/a.png">\n'
            '<img src="https://example.invalid/docs/media/a.png" alt="docs/media/a.png">\n'
            'Plain docs/media/a.png README.zh-CN.md docs/development.md LICENSE\n'
            '`![Code](docs/media/a.png)` and `<img src="docs/media/a.png">`\n'
            '```markdown\n![Code](docs/media/a.png)\n<img src="docs/media/a.png">\n```\n'
            '<!-- ![Comment](docs/media/a.png) <img src="docs/media/a.png"> -->\n'
            r'\![Escaped](docs/media/a.png) \[中文](README.zh-CN.md)'
        )
        self.assertEqual(readme_helper.render_readme(body), body)

    def test_three_document_links_keep_fragments_titles_and_angle_brackets(self):
        body = (
            '[中文](README.zh-CN.md "中文")\n'
            '[Development](<docs/development.md#setup> "Development")\n'
            '[MIT](LICENSE?raw=1)\n'
            '![Not a document link](LICENSE)\n'
        )
        expected = body.replace('](README.zh-CN.md', '](' + BLOB + 'README.zh-CN.md').replace(
            '](<docs/development.md', '](<' + BLOB + 'docs/development.md',
        ).replace('](LICENSE?raw=1)', '](' + BLOB + 'LICENSE?raw=1)')
        self.assertEqual(readme_helper.render_readme(body), expected)
        self.assertEqual(readme_helper.render_readme(expected), expected)

    def test_current_readme_and_both_payloads_use_same_idempotent_helper(self):
        body = (ROOT / "README.md").read_text(encoding="utf-8")
        expected = body.replace('src="docs/media/', 'src="' + RAW + 'docs/media/').replace(
            '](docs/media/', '](' + RAW + 'docs/media/',
        )
        # Normal links to screenshots stay relative. Only images are rewritten.
        for label in ("General settings", "About OpenAllay"):
            expected = expected.replace('[' + label + '](' + RAW, '[' + label + '](')
        for path in ("README.zh-CN.md", "docs/development.md", "LICENSE"):
            expected = expected.replace('](' + path + ')', '](' + BLOB + path + ')')
        self.assertEqual(readme_helper.render_readme(body), expected)
        self.assertEqual(readme_helper.render_readme(expected), expected)
        for name, payload in production_payloads(body).items():
            with self.subTest(path=name):
                self.assertEqual(payload["body"], expected)


if __name__ == "__main__":
    unittest.main()
