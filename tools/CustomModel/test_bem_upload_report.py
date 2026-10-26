import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import bem_upload_report


class BemUploadReportTests(unittest.TestCase):
    def test_bem14_report_uses_selected_package_metadata(self):
        example = Path(__file__).parent / 'examples/multi-resource/create_project.py'
        spec = importlib.util.spec_from_file_location('multi_resource_example', example)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            package = Path(temporary) / 'multi.bem'
            module.fixture().write(package)
            report = bem_upload_report.report(package)
            file_bytes = package.stat().st_size
            refused = subprocess.run([sys.executable, str(Path(bem_upload_report.__file__)),
                                      str(package), '--output', str(package)], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertIn('must not overwrite', refused.stderr)
            self.assertEqual(file_bytes, package.stat().st_size)
        self.assertGreater(report['geometry_upload_bytes'], 0)
        self.assertGreater(report['selected_decoded_payload_bytes'], 0)
        self.assertEqual(file_bytes, report['file_bytes'])
        self.assertNotIn('gpu_resident_bytes', report)
        with self.assertRaisesRegex(ValueError, '--options'):
            bem_upload_report.report_metadata(package, module.fixture().m,
                (b'BEM', 1, 4, 0, 0, 0, 0, 0), [], appearance='invalid')

    def test_chain_estimate_counts_all_mips(self):
        self.assertEqual(4 * (8 * 8 + 4 * 4 + 2 * 2), bem_upload_report.chain_bytes(8, 8, 3))
        self.assertEqual(16 * (2 * 2 + 1 + 1), bem_upload_report.chain_bytes(8, 8, 3, block=4))


if __name__ == '__main__':
    unittest.main()
