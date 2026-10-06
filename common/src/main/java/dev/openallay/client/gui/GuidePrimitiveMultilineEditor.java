package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftComponents;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** One product multiline primitive for native families without MultiLineEditBox. */
public class GuidePrimitiveMultilineEditor extends GuideNativeWidget implements GuideMultilineEditor {
    private static final int PADDING = 4;
    private final GuideWidget guideWidget = GuideNativeWidgets.wrap(this);
    private final Font font;
    private final Component placeholder;
    private final GuideMultilineTextState text = new GuideMultilineTextState();
    private double scroll;
    private boolean selecting;
    private boolean draggingScrollbar;
    private int frame;
    private long lastClick;
    private int lastClickIndex = -1;
    private char pendingHighSurrogate;
    private int characterLimit = Integer.MAX_VALUE;
    public int e2eCharacterLimit() { return characterLimit; }
    private long paintedFrames;
    private ProbeReceipt painted;
    public record ProbeReceipt(long frame, int cursor, int start, int end, int lines,
            int width, int caretX, int caretY, boolean focused) {}
    /** Cached actual paint facts; does not advance input or render. */
    public ProbeReceipt e2eReceipt() { return painted; }

    public GuidePrimitiveMultilineEditor(Font font, int x, int y, int width, int height,
            Component placeholder, Component narration) {
        super(x, y, width, height, narration);
        this.font = font;
        this.placeholder = placeholder;
        text.wrap(GuideComposerGeometry.contentWidth(width, PADDING * 2), text -> GuideNativeFont.width(font, GuideNativeFont.plain(text)));
    }
    @Override public GuideWidget widget() { return guideWidget; }
    @Override public String getValue() { return text.value(); }
    @Override public void setValue(String value, boolean bypassLineLimit) {
        // No line-count limit in this family. The independent character limit still applies.
        text.setValue(value);
        changed();
    }
    @Override public void setCharacterLimit(int limit) { text.characterLimit(limit); characterLimit = limit; changed(); }
    @Override public void setValueListener(Consumer<String> listener) { text.listener(listener); }
    @Override public void resize(int width, int height, int x, int y) {
        int contentWidth = GuideComposerGeometry.contentWidth(width, PADDING * 2);
        boolean reflow = getWidth() != width;
        guideSetBounds(x, y, width, height);
        if (reflow) text.wrap(contentWidth, text -> GuideNativeFont.width(font, GuideNativeFont.plain(text)));
        // Geometry changes clamp the existing offset; they do not move selection/caret/focus.
        clampScroll();
    }
    public void tick() { frame++; }
    @Override protected void onFocusedChanged(boolean focused) {
        frame = 0;
        pendingHighSurrogate = 0;
        if (!focused) { selecting = false; draggingScrollbar = false; }
    }
    private int lineHeight() { return GuideNativeFont.lineHeight(font) + 1; }
    private int viewportHeight() { return Math.max(1, getHeight() - PADDING * 2); }
    private double maximumScroll() { return Math.max(0, text.lines().size() * lineHeight() - viewportHeight()); }
    private void clampScroll() { scroll = Math.max(0, Math.min(maximumScroll(), scroll)); }
    private void changed() {
        frame = 0;
        double top = text.cursorLine() * lineHeight();
        if (top < scroll) scroll = top;
        if (top + lineHeight() > scroll + viewportHeight()) scroll = top + lineHeight() - viewportHeight();
        clampScroll();
    }
    private int indexAt(double mouseX, double mouseY) {
        int line = (int) Math.floor((mouseY - getY() - PADDING + scroll) / lineHeight());
        return text.indexAt(line, (int) Math.round(mouseX - getX() - PADDING));
    }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (!visible || !active || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        setFocused(true);
        if (maximumScroll() > 0 && mouseX >= getX() + getWidth() - PADDING) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        long now = System.currentTimeMillis();
        text.seek(index, (event.modifiers() & 1) != 0);
        if ((event.modifiers() & 1) == 0 && now - lastClick < 250 && index == lastClickIndex) {
            text.moveHorizontal(-1, false, true);
            text.moveHorizontal(1, true, true);
        }
        lastClick = now;
        lastClickIndex = index;
        selecting = true;
        changed();
        return true;
    }
    @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (!active || !visible || !isFocused() || button != 0) return false;
        if (draggingScrollbar) { scrollToMouse(mouseY); return true; }
        if (!selecting) return false;
        if (mouseY < getY() + PADDING) scroll -= lineHeight();
        if (mouseY > getY() + getHeight() - PADDING) scroll += lineHeight();
        clampScroll();
        text.seek(indexAt(mouseX, mouseY), true);
        changed();
        return true;
    }
    @Override public boolean guideMouseReleased(GuideInputMouse event) {
        int button = event.button();
        boolean handled = button == 0 && (selecting || draggingScrollbar);
        if (button == 0) { selecting = false; draggingScrollbar = false; }
        return handled;
    }
    @Override public boolean guideMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        scroll -= amount * lineHeight() * 3;
        clampScroll();
        return true;
    }
    private void scrollToMouse(double mouseY) {
        double thumb = Math.min(viewportHeight(), Math.max(12,
                viewportHeight() * (double) viewportHeight() / (text.lines().size() * lineHeight())));
        double travel = viewportHeight() - thumb;
        scroll = travel <= 0 ? 0 : (mouseY - getY() - PADDING - thumb / 2) / travel * maximumScroll();
        clampScroll();
    }
    @Override public boolean guideKeyPressed(GuideInputKey event) {
        int key = event.key();
        if (!isFocused() || !active || !visible) return false;
        pendingHighSurrogate = 0;
        boolean shift = event.hasShiftDown();
        boolean control = event.controlDown();
        if (control && key == GuideInputCodes.KEY_A) text.selectAll();
        else if (control && key == GuideInputCodes.KEY_C) GuideNativeInput.setClipboard(text.selected());
        else if (control && key == GuideInputCodes.KEY_X) {
            GuideNativeInput.setClipboard(text.selected());
            text.insert("");
        } else if (control && key == GuideInputCodes.KEY_V) text.insert(GuideNativeInput.getClipboard());
        else if (control && key == GuideInputCodes.KEY_Z) { if (shift) text.redo(); else text.undo(); }
        else if (control && key == GuideInputCodes.KEY_Y) text.redo();
        else switch (key) {
            case GuideInputCodes.KEY_RETURN, GuideInputCodes.KEY_NUMPADENTER -> text.insert("\n");
            case GuideInputCodes.KEY_BACK -> text.delete(-1, control);
            case GuideInputCodes.KEY_DELETE -> text.delete(1, control);
            case GuideInputCodes.KEY_LEFT -> text.moveHorizontal(-1, shift, control);
            case GuideInputCodes.KEY_RIGHT -> text.moveHorizontal(1, shift, control);
            case GuideInputCodes.KEY_UP -> text.moveVertical(-1, shift);
            case GuideInputCodes.KEY_DOWN -> text.moveVertical(1, shift);
            case GuideInputCodes.KEY_HOME -> text.home(shift, control);
            case GuideInputCodes.KEY_END -> text.end(shift, control);
            case GuideInputCodes.KEY_PAGEUP -> text.moveVertical(-Math.max(1, viewportHeight() / lineHeight()), shift);
            case GuideInputCodes.KEY_PAGEDOWN -> text.moveVertical(Math.max(1, viewportHeight() / lineHeight()), shift);
            default -> { return false; }
        }
        changed();
        return true;
    }
    @Override public boolean guideCharTyped(GuideInputCharacter event) {
        boolean handled = false;
        for (char character : Character.toChars(event.codePoint())) handled |= insertGuideCharacter(character);
        return handled;
    }
    private boolean insertGuideCharacter(char character) {
        if (!isFocused() || !active || !visible) return false;
        if (Character.isHighSurrogate(character)) { pendingHighSurrogate = character; return true; }
        if (Character.isLowSurrogate(character)) {
            if (pendingHighSurrogate == 0) return false;
            text.insert(new String(new char[]{pendingHighSurrogate, character}));
        } else if (character >= 32 && character != 127 && character != 167) text.insert(String.valueOf(character));
        else { pendingHighSurrogate = 0; return false; }
        pendingHighSurrogate = 0;
        changed();
        return true;
    }
    @Override protected void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF101010);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), isFocused() ? 0xFFFFFFFF : 0xFF707070);
        if (getHeight() <= PADDING * 2) return;
        graphics.enableScissor(getX() + PADDING, getY() + PADDING, getX() + getWidth() - PADDING, getY() + getHeight() - PADDING);
        try {
            int first = Math.max(0, (int) (scroll / lineHeight()));
            int last = Math.min(text.lines().size(), first + viewportHeight() / lineHeight() + 2);
            for (int i = first; i < last; i++) {
                GuideMultilineTextState.Line line = text.lines().get(i);
                int top = getY() + PADDING + i * lineHeight() - (int) scroll;
                int start = Math.max(line.start(), text.selectionStart());
                int end = Math.min(line.end(), text.selectionEnd());
                boolean newlineSelected = line.end() < text.value().length()
                        && text.value().charAt(line.end()) == '\n'
                        && text.selectionStart() <= line.end() && text.selectionEnd() > line.end();
                if (start < end || newlineSelected) {
                    int left = getX() + PADDING + GuideNativeFont.width(font, GuideNativeFont.plain(text.value().substring(line.start(), Math.min(start, line.end()))));
                    int right = newlineSelected ? getX() + getWidth() - PADDING
                            : getX() + PADDING + GuideNativeFont.width(font, GuideNativeFont.plain(text.value().substring(line.start(), end)));
                    graphics.fill(left, top, Math.max(left + 1, right), top + lineHeight(), 0xFF264F78);
                }
                graphics.text(font, text.value().substring(line.start(), line.end()), getX() + PADDING, top,
                        active ? 0xFFE0E0E0 : 0xFF707070, false);
            }
            if (text.value().isEmpty()) {
                graphics.text(font, placeholder, getX() + PADDING, getY() + PADDING, 0xFF707070, false);
            }
            if (isFocused() && active && (frame / 6) % 2 == 0) {
                GuideMultilineTextState.Line line = text.lines().get(text.cursorLine());
                int caretX = getX() + PADDING + GuideNativeFont.width(font, GuideNativeFont.plain(text.value().substring(line.start(), text.cursor())));
                int caretY = getY() + PADDING + text.cursorLine() * lineHeight() - (int) scroll;
                graphics.fill(caretX, caretY, caretX + 1, caretY + GuideNativeFont.lineHeight(font), 0xFFFFFFFF);
            }
        } finally { graphics.disableScissor(); }
        if (maximumScroll() > 0) {
            int thumb = Math.min(viewportHeight(), Math.max(12, viewportHeight() * viewportHeight()
                    / (text.lines().size() * lineHeight())));
            int top = getY() + PADDING + (int) ((viewportHeight() - thumb) * scroll / maximumScroll());
            graphics.fill(getX() + getWidth() - 3, top, getX() + getWidth() - 1, top + thumb, 0xFF909090);
        }
        if (Boolean.getBoolean("openallay.e2e.enabled")) {
            GuideMultilineTextState.Line caret = text.lines().get(text.cursorLine());
            painted = new ProbeReceipt(++paintedFrames, text.cursor(), text.selectionStart(), text.selectionEnd(),
                    text.lines().size(), getWidth(), getX() + PADDING + GuideNativeFont.width(font, GuideNativeFont.plain(text.value().substring(caret.start(), text.cursor()))),
                    getY() + PADDING + text.cursorLine() * lineHeight() - (int) scroll, isFocused());
        }
    }
    @Override protected void narrateGuideWidget(GuideNarration output) {
        output.add(GuideNarration.Part.TITLE, MinecraftComponents.getString(getMessage()) + ", " + text.value());
    }
}
