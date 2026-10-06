import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("old_native_gate", Path(__file__).with_name("run-packaged-builder-acceptance.py"))
launcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)

class PrimitiveNativeReceiptTests(unittest.TestCase):
    def valid(self):
        return {"nativeEditorReflow": {"widgetIdentity": 42,
            "narrow": {"frame": 1, "width": 40, "lines": 5, "cursor": 13, "start": 0, "end": 13},
            "wide": {"frame": 2, "width": 400, "lines": 2, "cursor": 13, "start": 0, "end": 13, "focused": True}},
            "nativeEditorCallbacks": {"text": "replacement", "paint": {"frame": 3, "start": 0, "end": 7},
                "clipboardCopyCutPaste": True, "undoRedo": True, "externalReplacement": True},
            "nativeEditorClipboardRestored": True, "nativeEditorStateRestored": True,
            "nativeToastMixed": {"occupied": [True]*5, "queuedCount": 0, "actualNativeTops": [0,64,128,-1,-1], "frames": [1,1,1,0,0]},
            "nativeToastTailWait": {"occupied": [True,True,True,True,False], "queuedCount": 2, "frames": [2,2,2,0,0]},
            "nativeToastReuse": {"queuedCount": 0, "actualNativeTops": [0,64,128,0,128], "frames": [3,3,3,1,1], "ownedCompletions": [1,0,0]},
            "nativeToastClear": {"occupied": [False]*5, "queuedCount": 0, "pendingEmpty": True, "actualNativeTops": [-1]*5}}

    def test_real_paints_and_native_fifo_geometry_required(self):
        launcher.validate_18182_native_primitives(self.valid())
        for key in ("nativeEditorReflow", "nativeToastMixed", "nativeToastTailWait", "nativeToastReuse", "nativeToastClear"):
            invalid = self.valid(); invalid.pop(key)
            with self.subTest(key=key), self.assertRaises(ValueError):
                launcher.validate_18182_native_primitives(invalid)

    def test_original_state_restoration_and_two_slot_positions_are_fatal(self):
        for field in ("nativeEditorClipboardRestored", "nativeEditorStateRestored"):
            invalid = self.valid(); invalid[field] = False
            with self.assertRaises(ValueError): launcher.validate_18182_native_primitives(invalid)
        invalid = self.valid(); invalid["nativeToastMixed"]["actualNativeTops"] = [0,32,64,-1,-1]
        with self.assertRaises(ValueError): launcher.validate_18182_native_primitives(invalid)
