#!/usr/bin/env python3
"""Finite source-binding guards for the reviewed release native API family cohort. No JVM/game."""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
GUI='dev/openallay/client/gui/'

def source(family,logical):
    return (ROOT/('common/src/targets/'+family+'/java')/logical).read_text()

class NativeApiFamilySources(unittest.TestCase):
    def test_primitive_callbacks_keep_native_family_arity_and_typed_capture(self):
        low=source('1.20.1',GUI+'GuideNativeWidget.java')
        upper=source('1.21.8',GUI+'GuideNativeWidget.java')
        for text in (low,upper):
            self.assertNotIn('net.minecraft.client.input.',text)
            for signature,call in [('keyPressed(int key, int scancode, int modifiers)','GuideNativeInput.capture(key, scancode, modifiers)'),
                ('charTyped(char character, int modifiers)','GuideNativeInput.capture(character, modifiers)'),
                ('mouseClicked(double x, double y, int button)','GuideNativeInput.capture(x, y, button)')]:
                self.assertIn(signature,text);self.assertIn(call,text)
            self.assertIn('paintGuideWidget(GuideGraphics.wrap(graphics), mouseX, mouseY, delta)',text)
            self.assertNotIn('scrollbar',text)
        self.assertIn('mouseScrolled(double x, double y, double vertical)',low)
        self.assertIn('mouseScrolled(double x, double y, double horizontal, double vertical)',upper)
        self.assertIn('this.width = width;',low);self.assertIn('this.height = height;',low)
        self.assertIn('setWidth(width);',upper);self.assertIn('setHeight(height);',upper)
        self.assertNotIn('setRectangle(',low)

    def test_native_named_id_keys_do_not_leak_the_identifier_rename(self):
        old=source('1.21.11','dev/openallay/platform/minecraft/MinecraftNativeRegistries.java')
        current=(ROOT/'common/src/main/java/dev/openallay/platform/minecraft/MinecraftNativeRegistries.java').read_text()
        self.assertIn('Collection<net.minecraft.resources.ResourceLocation> blockKeys()',old)
        self.assertIn('Collection<net.minecraft.resources.Identifier> blockKeys()',current)
        self.assertIn('return BLOCK.keySet()',old);self.assertIn('return BLOCK.keySet()',current)

    def test_sdl_native_code_constants_use_the_game_contract(self):
        text=source('26.3',GUI+'GuideInputCodes.java')
        self.assertIn('import com.mojang.blaze3d.platform.InputConstants;',text)
        self.assertNotIn('GLFW',text);self.assertNotIn('org.lwjgl.sdl',text)
        for name in ['KEY_A','KEY_C','KEY_X','KEY_Y','KEY_Z','KEY_ESCAPE','KEY_RETURN','KEY_NUMPADENTER','KEY_PAGEUP','KEY_PAGEDOWN','KEY_F6','KEY_F8','MOUSE_BUTTON_LEFT']:
            self.assertIn('= InputConstants.'+name+';',text)
        self.assertIn('KEY_BACK = InputConstants.KEY_BACKSPACE;',text)

    def test_older_native_image_and_widget_custody_are_not_feature_copies(self):
        image=source('1.21.4','dev/openallay/client/observation/MinecraftNativeImageCapture.java')
        editor=source('1.18.2','dev/openallay/guide/e2e/GuideNativeEditorE2EProbe.java')
        self.assertIn('GuideImageBitmaps.wrap(Screenshot.takeScreenshot',image)
        self.assertIn('GuideNativeWidgets.nativeWidget(editor.widget())',editor)
        self.assertIn('GuideNativeWidgets.nativeWidget(current.widget()) == widget',editor)
        queue=source('1.21.11','dev/openallay/server/NativeServerDeferredHandoff.java')
        self.assertIn('server.tell(new TickTask(server.getTickCount(), action));',queue)
        self.assertNotIn('server.execute(',queue)

if __name__=='__main__':unittest.main()
