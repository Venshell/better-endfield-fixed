import json
from pathlib import Path
import tempfile
import unittest

import build_bem14_target as builder


class BuildBem14TargetTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.asset = 'assets/char_body.prefab'
        self.database = {
            'schema': 1, 'kind': 'native_character_metadata',
            'source': {'snapshot': {'manifest_version': 'snapshot-1',
                                    'bundles': [{'path': 'bundles/android/body'}]}},
            'renderers': [{
                'id': 'renderer-1', 'mesh_id': 'mesh-1',
                'resource_asset_paths': [self.asset], 'resource_root': 'char_body',
                'path': 'char_body/Mesh_all/lod1/Body', 'renderer_kind': 'skinned',
                'mesh_name': 'BodyMesh', 'original_index_count': 3,
                'bones': [{'name': 'Root'}], 'materials': [{'name': 'BodyMaterial'}],
                'offline_references_complete': True, 'runtime_layout': None,
            }],
        }
        self.spec = {
            'schema': 1, 'kind': 'bem-resource-target-spec', 'id': 'char_body',
            'profile_id': 'char_body_profile', 'revision': 'draft_1',
            'snapshot': 'snapshot-1', 'target_kind': 'character',
            'resources': [{'id': 'body', 'asset_path': self.asset,
                           'platforms': ['android-arm64'], 'lod': 1}],
        }

    def test_android_profile_is_explicit_and_unverified(self):
        profile, manifest = builder.build_profile(self.database, self.spec)
        self.assertEqual(['android-arm64'], profile['target']['resources'][0]['platforms'])
        self.assertEqual('Mesh_all/lod1/Body', profile['target']['components'][0]['renderer_path'])
        self.assertFalse(profile['runtime_verified'])
        self.assertFalse(profile['conversion_ready'])
        self.assertEqual([], manifest['meshes'])
        self.assertEqual('keep', manifest['component_rules'][0]['candidates'][0]['operation'])
        self.assertEqual(['android-arm64'], profile['source_snapshot']['platforms'])

    def test_snapshot_and_renderer_identity_must_match(self):
        self.spec['snapshot'] = 'other-version'
        with self.assertRaisesRegex(ValueError, 'snapshot'):
            builder.build_profile(self.database, self.spec)
        self.spec['snapshot'] = 'snapshot-1'
        self.spec['resources'][0]['renderer_paths'] = ['Mesh_all/lod1/Missing']
        with self.assertRaisesRegex(ValueError, 'renderer'):
            builder.build_profile(self.database, self.spec)

    def test_outputs_are_bounded_and_do_not_replace_inputs(self):
        graph = self.root / 'graph.json'
        spec = self.root / 'spec.json'
        graph.write_text(json.dumps(self.database), encoding='utf-8')
        spec.write_text(json.dumps(self.spec), encoding='utf-8')
        output = self.root / 'profile.json'
        project = self.root / 'project.json'
        report = builder.create_profile(graph, spec, output, project)
        self.assertEqual(1, report['components'])
        self.assertEqual('bem-target-profile', json.loads(output.read_text(encoding='utf-8'))['kind'])
        self.assertEqual([], json.loads(project.read_text(encoding='utf-8'))['payload_files'])
        with self.assertRaisesRegex(ValueError, 'overwrite'):
            builder.create_profile(graph, spec, graph)
        with self.assertRaisesRegex(ValueError, 'size limit'):
            with spec.open('ab') as stream:
                stream.truncate(builder.MAX_SPEC_BYTES + 1)
            builder.create_profile(graph, spec, output)


if __name__ == '__main__':
    unittest.main()
