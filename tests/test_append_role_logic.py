import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.append_role_logic import (
    GENERATOR_PATH,
    ROLE_LOGIC,
    ROOT_DIR,
    TARGET_SECTION_MARKER,
    append_logic_to_file,
    apply_role_logic,
    main,
    run_corpus_verification,
)


class TestAppendRoleLogic(unittest.TestCase):
    """Harness for scripts/append_role_logic.py enforcing GARET_EVANS_19_BOUNDARY_TOPOLOGY (D006).

    Guarantees that role charter mutations maintain an explicit verification handoff
    to generate_philosopher_patterns.py --verify, ensuring role changes never diverge
    from the philosopher-pattern corpus.
    """

    def test_role_logic_covers_all_13_barcan_roles(self):
        self.assertEqual(len(ROLE_LOGIC), 13)
        for i in range(13):
            prefix = f"BARCAN-TAG-{i:02d}_"
            matching_keys = [k for k in ROLE_LOGIC.keys() if k.startswith(prefix)]
            self.assertEqual(
                len(matching_keys),
                1,
                f"Expected exactly 1 charter key for prefix {prefix}, got: {matching_keys}",
            )

    def test_append_logic_to_file_idempotence(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            test_file = Path(tmpdir) / "BARCAN-TAG-00_CODE-GUARDIAN.md"
            test_file.write_text(
                f"# Role 00\n\n{TARGET_SECTION_MARKER}\n\nExisting apparatus...",
                encoding="utf-8",
            )

            was_appended = append_logic_to_file(test_file, "\n### New block\n")
            self.assertFalse(was_appended)
            self.assertNotIn("### New block", test_file.read_text(encoding="utf-8"))

    def test_append_logic_to_file_modifies_when_missing(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            test_file = Path(tmpdir) / "BARCAN-TAG-00_CODE-GUARDIAN.md"
            test_file.write_text("# Role 00\n\nNo apparatus section yet.", encoding="utf-8")

            append_text = f"\n{TARGET_SECTION_MARKER}\n\nNew logic formula."
            was_appended = append_logic_to_file(test_file, append_text)

            self.assertTrue(was_appended)
            content = test_file.read_text(encoding="utf-8")
            self.assertIn(TARGET_SECTION_MARKER, content)
            self.assertIn("New logic formula.", content)

    def test_append_logic_to_file_dry_run(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            test_file = Path(tmpdir) / "BARCAN-TAG-00_CODE-GUARDIAN.md"
            initial_text = "# Role 00\n\nNo apparatus section yet."
            test_file.write_text(initial_text, encoding="utf-8")

            append_text = f"\n{TARGET_SECTION_MARKER}\n\nDry run text."
            was_appended = append_logic_to_file(test_file, append_text, dry_run=True)

            self.assertTrue(was_appended)
            # File content on disk must NOT have changed
            self.assertEqual(test_file.read_text(encoding="utf-8"), initial_text)

    def test_apply_role_logic_across_directory(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            dir_path = Path(tmpdir)
            # Create two files: one with section, one without
            f0 = dir_path / "BARCAN-TAG-00_CODE-GUARDIAN.md"
            f0.write_text(f"# Tag 00\n{TARGET_SECTION_MARKER}\n", encoding="utf-8")

            f1 = dir_path / "BARCAN-TAG-01_ACTUALIST-OBJECT.md"
            f1.write_text("# Tag 01\n", encoding="utf-8")

            result = apply_role_logic(target_dir=dir_path, dry_run=False)

            self.assertIn("BARCAN-TAG-01_ACTUALIST-OBJECT.md", result["updated"])
            self.assertIn("BARCAN-TAG-00_CODE-GUARDIAN.md", result["skipped"])
            self.assertEqual(len(result["missing"]), 11)  # 13 - 2 = 11 missing

            # Verify f1 now has the section
            self.assertIn(TARGET_SECTION_MARKER, f1.read_text(encoding="utf-8"))

    def test_corpus_verification_handoff_real(self):
        v_data = run_corpus_verification(GENERATOR_PATH)
        self.assertEqual(v_data.get("status"), "VERIFIED")
        self.assertEqual(v_data.get("barcan_files"), 13)
        self.assertEqual(v_data.get("philosophers"), 86)
        self.assertIn("checks_passed", v_data)

    def test_corpus_verification_handoff_fails_closed_on_missing_script(self):
        non_existent = ROOT_DIR / "scripts" / "non_existent_generator.py"
        with self.assertRaises(FileNotFoundError):
            run_corpus_verification(non_existent)

    def test_corpus_verification_handoff_fails_closed_on_script_error(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            broken_script = Path(tmpdir) / "broken_generator.py"
            broken_script.write_text("import sys; sys.exit(42)\n", encoding="utf-8")

            with self.assertRaises(RuntimeError) as ctx:
                run_corpus_verification(broken_script)
            self.assertIn("exit code 42", str(ctx.exception))

    def test_main_cli_verify(self):
        with self.assertRaises(SystemExit) as cm:
            main(["--verify"])
        self.assertEqual(cm.exception.code, 0)

    def test_main_cli_dry_run(self):
        # Running dry-run against the real repo root with verification must exit 0 cleanly
        with patch("sys.exit") as mock_exit:
            main(["--dry-run"])
            # If sys.exit was called, ensure it was 0
            if mock_exit.called:
                mock_exit.assert_called_with(0)


if __name__ == "__main__":
    unittest.main()
