import importlib.util
import pathlib
import unittest
from unittest.mock import patch


SCRIPT_PATH = pathlib.Path(__file__).with_name("migrate_aliyun_oss_image_metadata.py")
SPEC = importlib.util.spec_from_file_location("oss_metadata_migration", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class MigrationScopeTest(unittest.TestCase):

    def test_prefix_preserves_significant_whitespace(self):
        for prefix in ("images/", " images/", "images/ "):
            with self.subTest(prefix=prefix):
                with patch("sys.argv", [str(SCRIPT_PATH), "--prefix", prefix]):
                    args = MODULE.parse_args()

                self.assertEqual(prefix, MODULE.resolve_prefix(args))

    def test_blank_prefix_requires_explicit_whole_bucket_scope(self):
        for prefix in ("", " ", "\t"):
            with self.subTest(prefix=prefix):
                with patch("sys.argv", [str(SCRIPT_PATH), "--prefix", prefix]):
                    args = MODULE.parse_args()

                with self.assertRaisesRegex(SystemExit, "--all-objects"):
                    MODULE.resolve_prefix(args)

    def test_all_objects_resolves_to_empty_oss_prefix(self):
        with patch("sys.argv", [str(SCRIPT_PATH), "--all-objects"]):
            args = MODULE.parse_args()

        self.assertTrue(args.all_objects)
        self.assertEqual("", MODULE.resolve_prefix(args))

    def test_prefix_and_all_objects_are_mutually_exclusive(self):
        with patch("sys.argv", [str(SCRIPT_PATH), "--prefix", "images/", "--all-objects"]):
            with self.assertRaises(SystemExit):
                MODULE.parse_args()

    def test_scope_is_required(self):
        with patch("sys.argv", [str(SCRIPT_PATH)]):
            with self.assertRaises(SystemExit):
                MODULE.parse_args()


if __name__ == "__main__":
    unittest.main()
