import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('package_release', Path(__file__).parents[1] / 'package-release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleasePackagingTest(unittest.TestCase):
    def test_tag_must_match_app_version_and_cannot_contain_shell_or_path_syntax(self):
        for tag in ['v13.7.0', 'v13.7.1-preview.0', 'v13.7.1;echo hacked', '../v13.7.1', 'v13.7.1\n']:
            with self.assertRaises(ValueError, msg=tag):
                release.validate_tag(tag, '13.7.1')
        release.validate_tag('v13.7.1-preview.1', '13.7.1')
        release.validate_tag('v13.7.1', '13.7.1')

    def test_source_package_keeps_wrapper_but_excludes_signing_keys_env_and_build_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ['README.md', 'app/src/Player.kt', 'gradle/wrapper/gradle-wrapper.jar', 'key.jks', 'key.keystore', 'signing/private.key', '.env.production', 'local.properties', 'app/build/token.txt', '.gradle/cache.txt', 'release-files/build-report.json']:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture')
            with patch.object(release, 'ROOT', root):
                paths = {relative for _, relative in release.source_files()}
            self.assertEqual({'README.md', 'app/src/Player.kt', 'gradle/wrapper/gradle-wrapper.jar'}, paths)

    def test_source_package_does_not_follow_links_to_private_files(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as private:
            root = Path(directory)
            target = Path(private) / 'secret.txt'
            target.write_text('private fixture')
            (root / 'not-source.txt').symlink_to(target)
            with patch.object(release, 'ROOT', root):
                self.assertEqual([], list(release.source_files()))

    def test_digest_matches_a_known_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'data'
            path.write_bytes(b'abc')
            self.assertEqual('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', release.digest(path))


if __name__ == '__main__':
    unittest.main()
