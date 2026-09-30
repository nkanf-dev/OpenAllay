"""Local CLI regression tests for curated and legacy release notes. No network needed."""
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "generate-release-notes.py"
REPOSITORY = "example/OpenAllay"


def module():
    spec = spec_from_file_location("generate_release_notes", SCRIPT)
    result = module_from_spec(spec)
    sys.modules[spec.name] = result
    spec.loader.exec_module(result)
    return result


notes = module()


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.environment = os.environ.copy()
        self.environment.pop("GH_TOKEN", None)
        self.environment["GIT_CONFIG_GLOBAL"] = os.devnull
        self.environment["GIT_CONFIG_NOSYSTEM"] = "1"
        self.git("init", "-q")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "test@example.invalid")
        self.previous_commit = self.commit("chore: base release")
        self.tag("v0.2.2")
        self.current_commit = self.commit("feat: clearer player answers")
        self.tag("v0.2.4")
        self.output = self.root / "release-notes.md"
        self.gh_calls = self.root / "gh-calls.json"
        binary = self.root / "bin"
        binary.mkdir()
        gh = binary / "gh"
        gh.write_text(
            f"#!{sys.executable}\n"
            "import json, os, pathlib, sys\n"
            "pathlib.Path(os.environ['GH_CALLS_FILE']).write_text(json.dumps(sys.argv[1:]))\n"
            "print(json.dumps({'body': os.environ.get('GH_NOTES_BODY', '## GitHub changes\\n\\n- Merged player improvement.')}))\n",
            encoding="utf-8",
        )
        gh.chmod(0o755)
        self.environment["PATH"] = str(binary) + os.pathsep + self.environment["PATH"]
        self.environment["GH_CALLS_FILE"] = str(self.gh_calls)

    def git(self, *args, strip=True):
        result = subprocess.run(
            ["git", *args], cwd=self.root, env=self.environment, check=True,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        return result.stdout.strip() if strip else result.stdout

    def commit(self, subject):
        self.git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-qm", subject)
        return self.git("rev-parse", "HEAD")

    def tag(self, name, target="HEAD"):
        self.git("-c", "tag.gpgsign=false", "tag", "-a", name, target, "-m", name)

    def curated(self, tag="v0.2.4", body=None, tagged=True):
        if body is None:
            body = f"# OpenAllay {tag}\n\n- Clearer answers in the game.\n- 更清晰的游戏内回答。\n"
        path = self.root / "docs" / "releases" / f"{tag[1:]}.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
        if tagged:
            if self.git("tag", "--list", tag) == tag:
                self.git("tag", "-d", tag)
            self.git("add", "--", str(path.relative_to(self.root)))
            commit = self.commit("docs: write curated release notes")
            self.tag(tag)
            if tag == "v0.2.4":
                self.current_commit = commit
        return body

    def render(self, tag="v0.2.4", repository=None, token=None, directory=None):
        environment = self.environment.copy()
        if token is not None:
            environment["GH_TOKEN"] = token
        request = [sys.executable, str(SCRIPT), tag, "--output", str(self.output)]
        if repository is not None:
            request.extend(("--repository", repository))
        return subprocess.run(
            request, cwd=directory or self.root, env=environment, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )

    def successful_notes(self, **kwargs):
        result = self.render(**kwargs)
        self.assertEqual(result.returncode, 0, result.stderr)
        return self.output.read_text(encoding="utf-8")

    def test_curated_notes_need_no_token_and_make_no_github_call(self):
        body = self.curated()
        text = self.successful_notes(repository=REPOSITORY)
        changelog = f"**Full changelog:** https://github.com/{REPOSITORY}/compare/v0.2.2...v0.2.4\n"
        self.assertEqual(text, body.rstrip() + "\n\n" + changelog)
        self.assertEqual(text.count("**Full changelog:**"), 1)
        self.assertFalse(self.gh_calls.exists())
        for legacy in ("## Commits", "## Contributors", "clearer player answers", self.current_commit[:7]):
            self.assertNotIn(legacy, text)

    def test_curated_notes_do_not_call_github_even_with_token(self):
        self.curated()
        self.successful_notes(repository=REPOSITORY, token="local-test-token")
        self.assertFalse(self.gh_calls.exists())

    def test_curated_notes_without_repository_are_unchanged(self):
        body = self.curated(body="# OpenAllay v0.2.4\n\n- Player-facing change.\n\n")
        self.assertEqual(self.successful_notes(), body.rstrip() + "\n")
        self.assertFalse(self.gh_calls.exists())

    def test_curated_first_release_has_no_compare_link(self):
        self.git("tag", "-d", "v0.2.2")
        body = self.curated()
        self.assertEqual(self.successful_notes(repository=REPOSITORY), body)
        self.assertFalse(self.gh_calls.exists())

    def test_curated_prerelease_and_build_metadata_use_exact_filename(self):
        tag = "v0.2.5-rc.1+build.7"
        body = self.curated(tag)
        text = self.successful_notes(tag=tag, repository=REPOSITORY)
        self.assertTrue(text.startswith(body.rstrip() + "\n\n"))
        self.assertIn(f"/compare/v0.2.4...{tag}", text)
        self.assertEqual(text.count("**Full changelog:**"), 1)

    def test_curated_notes_still_resolve_current_tag(self):
        tag = "v9.9.9"
        self.curated(tag, tagged=False)
        result = self.render(tag=tag)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertFalse(self.gh_calls.exists())

    def test_previous_release_filters_non_semver_and_lightweight_tags(self):
        self.git("tag", "v0.2.3")
        self.tag("release-latest")
        self.tag("v01.2.3")
        self.curated()
        text = self.successful_notes(repository=REPOSITORY)
        self.assertIn("/compare/v0.2.2...v0.2.4", text)
        self.assertNotIn("/compare/v0.2.3...", text)

    def test_previous_release_must_be_reachable_from_current_tag(self):
        self.curated()
        self.git("checkout", "-q", "-b", "unrelated-release", self.previous_commit)
        self.commit("feat: unrelated branch")
        self.tag("v0.2.3")
        self.assertIn("/compare/v0.2.2...v0.2.4", self.successful_notes(repository=REPOSITORY))

    def test_missing_curated_notes_preserve_local_legacy_output(self):
        sha = self.current_commit[:7]
        expected = (
            "# OpenAllay v0.2.4\n\n## Changes\n\n### Features\n\n"
            f"- feat: clearer player answers (`{sha}`)\n\n"
            f"## Commits\n\n- `{sha}` feat: clearer player answers\n\n"
            "## Contributors\n\n- Release Test\n"
        )
        self.assertEqual(self.successful_notes(), expected)
        self.assertFalse(self.gh_calls.exists())

    def test_missing_curated_notes_preserve_github_legacy_output(self):
        text = self.successful_notes(repository=REPOSITORY, token="local-test-token")
        sha = self.current_commit[:7]
        expected = (
            "# OpenAllay v0.2.4\n\n## GitHub changes\n\n- Merged player improvement.\n\n"
            f"## Commits\n\n- [`{sha}`](https://github.com/{REPOSITORY}/commit/{self.current_commit}) "
            "feat: clearer player answers\n\n## Contributors\n\n- Release Test\n\n"
            f"**Full changelog:** https://github.com/{REPOSITORY}/compare/v0.2.2...v0.2.4\n"
        )
        self.assertEqual(text, expected)
        self.assertEqual(json.loads(self.gh_calls.read_text(encoding="utf-8")), [
            "api", "--method", "POST", f"repos/{REPOSITORY}/releases/generate-notes",
            "-f", "tag_name=v0.2.4", "-f", "previous_tag_name=v0.2.2",
        ])

    def test_missing_curated_notes_still_require_token_for_github(self):
        result = self.render(repository=REPOSITORY)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GH_TOKEN is required", result.stderr)
        self.assertFalse(self.gh_calls.exists())

    def test_other_versions_curated_file_does_not_replace_legacy_notes(self):
        self.curated("v0.2.5")
        self.assertIn("## Commits", self.successful_notes())

    def test_curated_notes_ignore_modified_worktree_document(self):
        body = self.curated()
        self.curated(body="Changed working-tree notes.\n", tagged=False)
        self.assertEqual(self.successful_notes(), body)

    def test_curated_notes_do_not_need_a_worktree_document(self):
        body = self.curated()
        (self.root / "docs" / "releases" / "0.2.4.md").unlink()
        self.assertEqual(self.successful_notes(), body)

    def test_curated_notes_resolve_from_checkout_subdirectory(self):
        body = self.curated()
        self.assertEqual(self.successful_notes(directory=self.root / "docs"), body)

    def test_untracked_document_for_existing_tag_preserves_legacy_fallback(self):
        legacy = self.successful_notes()
        self.curated(tagged=False)
        self.assertEqual(self.successful_notes(), legacy)
        result = self.render(repository=REPOSITORY)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GH_TOKEN is required", result.stderr)
        self.assertFalse(self.gh_calls.exists())

    def test_later_committed_document_does_not_replace_existing_tag_notes(self):
        legacy = self.successful_notes()
        path = self.root / "docs" / "releases" / "0.2.4.md"
        self.curated(tagged=False)
        self.git("add", "--", str(path.relative_to(self.root)))
        self.commit("docs: notes added after release")
        self.assertEqual(self.successful_notes(), legacy)

    def test_cli_keeps_strict_semver_validation_with_or_without_curated_file(self):
        for tag in ("0.2.4", "v01.2.4", "v0.2.4-01", "v0.2.4-rc..1", "v0.2.4+"):
            for curated in (False, True):
                with self.subTest(tag=tag, curated=curated):
                    if curated:
                        self.curated(tag, tagged=False)
                    result = self.render(tag=tag)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("tag must be strict SemVer with a v prefix", result.stderr)
                    self.assertFalse(self.output.exists())
                    self.assertFalse(self.gh_calls.exists())

    def test_curated_notes_skip_commit_collection(self):
        self.curated()
        with patch.object(notes, "git", side_effect=self.git), \
                patch.object(notes, "previous_tag", wraps=notes.previous_tag) as previous, \
                patch.object(notes, "commits", side_effect=AssertionError("legacy history read")), \
                patch.object(notes, "github_generated_notes", side_effect=AssertionError("GitHub call")):
            text = notes.render("v0.2.4", REPOSITORY)
        previous.assert_called_once_with("v0.2.4", self.current_commit)
        self.assertNotIn("## Commits", text)


if __name__ == "__main__":
    unittest.main()
