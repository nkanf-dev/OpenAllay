package dev.openallay.guide.ui;

import java.util.List;

/** Deterministic responsive layout; rendering code consumes these rectangles. */
@dev.openallay.value.ValueType(GuideUiLayout.ValueSchemaProvider.class)
public final class GuideUiLayout {
    private final boolean narrow;
    private final Rect topBar;
    private final Rect sessionRail;
    private final Rect transcript;
    private final Rect progress;
    private final Rect composer;
    private final Rect detail;
    private final boolean detailOverlay;
    private final Rect telemetry;
    private final Header header;
    private final ComposerControls composerControls;
    public GuideUiLayout(boolean narrow, Rect topBar, Rect sessionRail, Rect transcript, Rect progress, Rect composer, Rect detail, boolean detailOverlay, Rect telemetry, Header header, ComposerControls composerControls) {
        this.narrow = narrow;
        this.topBar = topBar;
        this.sessionRail = sessionRail;
        this.transcript = transcript;
        this.progress = progress;
        this.composer = composer;
        this.detail = detail;
        this.detailOverlay = detailOverlay;
        this.telemetry = telemetry;
        this.header = header;
        this.composerControls = composerControls;
    }
    public boolean narrow() { return narrow; }
    public Rect topBar() { return topBar; }
    public Rect sessionRail() { return sessionRail; }
    public Rect transcript() { return transcript; }
    public Rect progress() { return progress; }
    public Rect composer() { return composer; }
    public Rect detail() { return detail; }
    public boolean detailOverlay() { return detailOverlay; }
    public Rect telemetry() { return telemetry; }
    public Header header() { return header; }
    public ComposerControls composerControls() { return composerControls; }
private static final int MIN_TRANSCRIPT_HEIGHT = 54;
private static final int NOTICE_HEIGHT = 10;
private static final int TELEMETRY_HEIGHT = 14;
private static final int TELEMETRY_CARD_HEIGHT = 80;
public static GuideUiLayout calculate(int width, int height, boolean detailOpen) {
        return calculate(width, height, detailOpen, true);
    }
public static GuideUiLayout calculate(int width, int height, boolean detailOpen, boolean showRail) {
        return calculate(width, height, detailOpen, 120, 60, 48, 54, true,
                false, false, 0, showRail);
    }
public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings) {
        return calculate(width, height, detailOpen, titleWidth, sessionsWidth, exportWidth,
                refreshWidth, settings, false, false, 0);
    }
public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings,
            boolean images, boolean activeMode, int pendingMessages) {
        return calculate(width, height, detailOpen, titleWidth, sessionsWidth, exportWidth,
                refreshWidth, settings, images, activeMode, pendingMessages, true);
    }
public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings,
            boolean images, boolean activeMode, int pendingMessages, boolean showRail) {
        if (width < 240 || height < 180) throw new IllegalArgumentException("screen is too small");
        int margin = height < 240 ? 4 : 8;
        int available = width - margin * 2;
        boolean narrow = width < 560;
        int railWidth = narrow || !showRail ? 0 : 128;
        boolean telemetryCard = railWidth > 0 && height >= 240 && (!detailOpen || width >= 760);
        int progressHeight = activeMode ? (height < 240 ? 12 : 22) : 0;
        boolean footer = activeMode || pendingMessages > 0;
        // On short screens attachments and the queue remain compact summaries.
        int desiredComposerHeight = height < 360
                ? Math.min(68, 44 + (images ? 14 : 0) + (footer ? 14 : 0))
                : 44 + (images ? 42 : 0) + (footer ? 20 : 0)
                        + Math.min(2, Math.max(0, pendingMessages - 1)) * 20;
        int composerBottom = height - margin - (telemetryCard ? 0 : TELEMETRY_HEIGHT + 2);
        int minimumComposerHeight = Math.max(44, 24 + (images ? 14 : 0) + (footer ? 14 : 0));
        boolean stackedHeaderAllowed = composerBottom - (margin + 40 + 2) - MIN_TRANSCRIPT_HEIGHT
                - progressHeight - NOTICE_HEIGHT - 2 >= minimumComposerHeight;
        Header header = Header.calculate(margin, margin, available,
                titleWidth, sessionsWidth, settings, stackedHeaderAllowed);
        Rect topBar = new Rect(margin, margin, available, header.height());
        int bodyTop = topBar.bottom() + 2;
        int composerBudget = composerBottom - bodyTop - MIN_TRANSCRIPT_HEIGHT
                - progressHeight - NOTICE_HEIGHT - 2;
        int composerHeight = Math.min(desiredComposerHeight, composerBudget);
        int composerTop = composerBottom - composerHeight;
        int progressTop = composerTop - NOTICE_HEIGHT - progressHeight;
        int bodyHeight = progressTop - 2 - bodyTop;
        boolean inlineDetail = detailOpen && width >= 760;
        int detailWidth = inlineDetail ? 220 : 0;
        int transcriptLeft = margin + railWidth + (railWidth == 0 ? 0 : margin);
        int transcriptRight = width - margin - detailWidth - (detailWidth == 0 ? 0 : margin);
        Rect transcript = new Rect(transcriptLeft, bodyTop, transcriptRight - transcriptLeft, bodyHeight);
        int overlayWidth = Math.min(available, Math.max(220, width * 3 / 4));
        Rect detail = !detailOpen
                ? Rect.EMPTY
                : inlineDetail
                        ? new Rect(width - margin - detailWidth, bodyTop, detailWidth, bodyHeight)
                        : new Rect((width - overlayWidth) / 2, bodyTop, overlayWidth, bodyHeight);
        Rect composer = new Rect(transcriptLeft, composerTop, transcript.width(), composerHeight);
        int telemetryBubbleWidth = Math.min(180, composer.width());
        Rect telemetry = telemetryCard
                ? new Rect(margin, composerBottom - TELEMETRY_CARD_HEIGHT, railWidth, TELEMETRY_CARD_HEIGHT)
                : new Rect(composer.right() - telemetryBubbleWidth, composer.bottom() + 2,
                        telemetryBubbleWidth, TELEMETRY_HEIGHT);
        int railBottom = telemetryCard ? Math.min(transcript.bottom(), telemetry.y() - 2) : transcript.bottom();
        return new GuideUiLayout(
                narrow,
                topBar,
                railWidth == 0 ? Rect.EMPTY : new Rect(margin, bodyTop, railWidth, railBottom - bodyTop),
                transcript,
                new Rect(transcriptLeft, progressTop, transcript.width(), progressHeight),
                composer,
                detail,
                detailOpen && !inlineDetail,
                telemetry,
                header,
                ComposerControls.calculate(composer, activeMode));
    }
public ComposerExtras composerExtras(boolean images, boolean activeMode, int pendingMessages) {
        Rect area = composerControls.input();
        boolean footer = activeMode || pendingMessages > 0;
        int footerHeight = footer ? 12 : 0;
        int imageHeight = images ? 12 : 0;
        int gaps = (images ? 2 : 0) + (footer ? 2 : 0);
        int remaining = area.height() - 24 - footerHeight - imageHeight - gaps;
        int inputHeight = 24 + Math.min(20, remaining);
        remaining -= inputHeight - 24;
        int footerExtra = footer ? Math.min(6, remaining) : 0;
        footerHeight += footerExtra;
        remaining -= footerExtra;
        int imageExtra = images ? Math.min(28, remaining) : 0;
        imageHeight += imageExtra;
        remaining -= imageExtra;
        int pendingHeight = Math.min(Math.min(2, Math.max(0, pendingMessages - 1)) * 20,
                remaining / 20 * 20);
        inputHeight += remaining - pendingHeight;
        Rect input = new Rect(area.x(), area.y(), area.width(), inputHeight);
        Rect imageStrip = images
                ? new Rect(area.x(), input.bottom() + 2, area.width(), imageHeight) : Rect.EMPTY;
        int footerY = input.bottom() + (images ? imageHeight + 2 : 0) + (footer ? 2 : 0);
        Rect footerRect = footer
                ? new Rect(area.x(), footerY, area.width(), footerHeight) : Rect.EMPTY;
        Rect pending = pendingHeight == 0 ? Rect.EMPTY
                : new Rect(area.x(), footerRect.bottom(), area.width(), pendingHeight);
        return new ComposerExtras(input, imageStrip, footerRect, pending);
    }
@dev.openallay.value.ValueType(ComposerExtras.ValueSchemaProvider.class)
public static final class ComposerExtras {
    private final Rect input;
    private final Rect images;
    private final Rect footer;
    private final Rect pending;
    public ComposerExtras(Rect input, Rect images, Rect footer, Rect pending) {
        this.input = input;
        this.images = images;
        this.footer = footer;
        this.pending = pending;
    }
    public Rect input() { return input; }
    public Rect images() { return images; }
    public Rect footer() { return footer; }
    public Rect pending() { return pending; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ComposerExtras)) return false;
        ComposerExtras that = (ComposerExtras) other;
        return java.util.Objects.equals(input, that.input) && java.util.Objects.equals(images, that.images) && java.util.Objects.equals(footer, that.footer) && java.util.Objects.equals(pending, that.pending);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(input);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        hash = 31 * hash + java.util.Objects.hashCode(footer);
        hash = 31 * hash + java.util.Objects.hashCode(pending);
        return hash;
    }
    @Override public String toString() { return "ComposerExtras[input=" + input + ", images=" + images + ", footer=" + footer + ", pending=" + pending + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ComposerExtras> schema() {
            return new dev.openallay.value.ValueSchema<>(ComposerExtras.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ComposerExtras>>asList(new dev.openallay.value.ValueSchema.Component<>(ComposerExtras.class, "input", ComposerExtras::input), new dev.openallay.value.ValueSchema.Component<>(ComposerExtras.class, "images", ComposerExtras::images), new dev.openallay.value.ValueSchema.Component<>(ComposerExtras.class, "footer", ComposerExtras::footer), new dev.openallay.value.ValueSchema.Component<>(ComposerExtras.class, "pending", ComposerExtras::pending)), arguments -> new ComposerExtras((Rect) arguments[0], (Rect) arguments[1], (Rect) arguments[2], (Rect) arguments[3]));
        }
    }
}
public Rect composerNotice() {
        return new Rect(composer.x(), composer.y() - NOTICE_HEIGHT, composer.width(), NOTICE_HEIGHT);
    }
public boolean telemetryCard() {
        return telemetry.height() == TELEMETRY_CARD_HEIGHT;
    }
@dev.openallay.value.ValueType(Header.ValueSchemaProvider.class)
public static final class Header {
    private final Rect title;
    private final Rect status;
    private final Rect sessions;
    private final Rect create;
    private final Rect delete;
    private final Rect export;
    private final Rect model;
    private final Rect refresh;
    private final Rect settings;
    private final Rect overflow;
    private final int height;
    public Header(Rect title, Rect status, Rect sessions, Rect create, Rect delete, Rect export, Rect model, Rect refresh, Rect settings, Rect overflow, int height) {
        this.title = title;
        this.status = status;
        this.sessions = sessions;
        this.create = create;
        this.delete = delete;
        this.export = export;
        this.model = model;
        this.refresh = refresh;
        this.settings = settings;
        this.overflow = overflow;
        this.height = height;
    }
    public Rect title() { return title; }
    public Rect status() { return status; }
    public Rect sessions() { return sessions; }
    public Rect create() { return create; }
    public Rect delete() { return delete; }
    public Rect export() { return export; }
    public Rect model() { return model; }
    public Rect refresh() { return refresh; }
    public Rect settings() { return settings; }
    public Rect overflow() { return overflow; }
    public int height() { return height; }
static Header calculate(
                int x, int y, int width, int titleWidth, int sessionsWidth,
                boolean settings, boolean stackedAllowed) {
            int settingsWidth = settings ? 20 : 0;
            int overflowWidth = 20;
            int gaps = settings ? 12 : 8;
            int fixedControls = settingsWidth + overflowWidth + gaps;
            int minimumControls = 20 + 20 + fixedControls;
            int wantedTitle = Math.max(1, titleWidth);
            // The name owns its width before the model label or secondary controls.
            // A second row is bounded and admitted only by the shared body/input budget.
            boolean stacked = stackedAllowed && wantedTitle + 8 + minimumControls > width;
            int visibleTitleWidth = Math.min(wantedTitle, width - (stacked ? 8 : minimumControls + 8));
            int controlBudget = stacked ? width : width - visibleTitleWidth - 8;
            int sessions = Math.min(Math.min(96, Math.max(48, sessionsWidth)),
                    controlBudget - fixedControls - 20);
            int modelWidth = Math.min(128, controlBudget - fixedControls - sessions);
            int total = sessions + modelWidth + fixedControls;
            int cursor = x + width - total;
            int controlsY = y + (stacked ? 18 : 2);
            int headerHeight = stacked ? 40 : 24;
            Rect title = new Rect(x + 4, y + (stacked ? 4 : 6), visibleTitleWidth, 12);
            Rect sessionsRect = new Rect(cursor, controlsY, sessions, 20);
            cursor = sessionsRect.right() + 4;
            Rect model = new Rect(cursor, controlsY, modelWidth, 20);
            cursor = model.right() + 4;
            Rect overflow = new Rect(cursor, controlsY, overflowWidth, 20);
            cursor = overflow.right() + 4;
            Rect settingsRect = settings ? new Rect(cursor, controlsY, settingsWidth, 20) : Rect.EMPTY;
            return new Header(title, new Rect(x, y + headerHeight - 2, width, 0), sessionsRect,
                    Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, model, Rect.EMPTY, settingsRect, overflow, headerHeight);
        }
public List<Rect> controls() {
            return dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listOf(sessions, model, overflow, settings).stream()
                    .filter(rect -> rect.width() > 0 && rect.height() > 0));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Header)) return false;
        Header that = (Header) other;
        return java.util.Objects.equals(title, that.title) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(sessions, that.sessions) && java.util.Objects.equals(create, that.create) && java.util.Objects.equals(delete, that.delete) && java.util.Objects.equals(export, that.export) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(refresh, that.refresh) && java.util.Objects.equals(settings, that.settings) && java.util.Objects.equals(overflow, that.overflow) && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(sessions);
        hash = 31 * hash + java.util.Objects.hashCode(create);
        hash = 31 * hash + java.util.Objects.hashCode(delete);
        hash = 31 * hash + java.util.Objects.hashCode(export);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(refresh);
        hash = 31 * hash + java.util.Objects.hashCode(settings);
        hash = 31 * hash + java.util.Objects.hashCode(overflow);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Header[title=" + title + ", status=" + status + ", sessions=" + sessions + ", create=" + create + ", delete=" + delete + ", export=" + export + ", model=" + model + ", refresh=" + refresh + ", settings=" + settings + ", overflow=" + overflow + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Header> schema() {
            return new dev.openallay.value.ValueSchema<>(Header.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Header>>asList(new dev.openallay.value.ValueSchema.Component<>(Header.class, "title", Header::title), new dev.openallay.value.ValueSchema.Component<>(Header.class, "status", Header::status), new dev.openallay.value.ValueSchema.Component<>(Header.class, "sessions", Header::sessions), new dev.openallay.value.ValueSchema.Component<>(Header.class, "create", Header::create), new dev.openallay.value.ValueSchema.Component<>(Header.class, "delete", Header::delete), new dev.openallay.value.ValueSchema.Component<>(Header.class, "export", Header::export), new dev.openallay.value.ValueSchema.Component<>(Header.class, "model", Header::model), new dev.openallay.value.ValueSchema.Component<>(Header.class, "refresh", Header::refresh), new dev.openallay.value.ValueSchema.Component<>(Header.class, "settings", Header::settings), new dev.openallay.value.ValueSchema.Component<>(Header.class, "overflow", Header::overflow), new dev.openallay.value.ValueSchema.Component<>(Header.class, "height", Header::height)), arguments -> new Header((Rect) arguments[0], (Rect) arguments[1], (Rect) arguments[2], (Rect) arguments[3], (Rect) arguments[4], (Rect) arguments[5], (Rect) arguments[6], (Rect) arguments[7], (Rect) arguments[8], (Rect) arguments[9], (Integer) arguments[10]));
        }
    }
}
@dev.openallay.value.ValueType(ComposerControls.ValueSchemaProvider.class)
public static final class ComposerControls {
    private final Rect input;
    private final Rect send;
    private final Rect stop;
    private final Rect retry;
    public ComposerControls(Rect input, Rect send, Rect stop, Rect retry) {
        this.input = input;
        this.send = send;
        this.stop = stop;
        this.retry = retry;
    }
    public Rect input() { return input; }
    public Rect send() { return send; }
    public Rect stop() { return stop; }
    public Rect retry() { return retry; }
static ComposerControls calculate(Rect area, boolean activeMode) {
            int actionWidth = 54;
            int x = area.right() - actionWidth;
            return new ComposerControls(
                    new Rect(area.x(), area.y(), area.width() - actionWidth - 6, area.height()),
                    new Rect(x, area.y(), actionWidth, 20),
                    activeMode ? new Rect(x, area.y() + 24, actionWidth, 20) : Rect.EMPTY,
                    Rect.EMPTY);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ComposerControls)) return false;
        ComposerControls that = (ComposerControls) other;
        return java.util.Objects.equals(input, that.input) && java.util.Objects.equals(send, that.send) && java.util.Objects.equals(stop, that.stop) && java.util.Objects.equals(retry, that.retry);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(input);
        hash = 31 * hash + java.util.Objects.hashCode(send);
        hash = 31 * hash + java.util.Objects.hashCode(stop);
        hash = 31 * hash + java.util.Objects.hashCode(retry);
        return hash;
    }
    @Override public String toString() { return "ComposerControls[input=" + input + ", send=" + send + ", stop=" + stop + ", retry=" + retry + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ComposerControls> schema() {
            return new dev.openallay.value.ValueSchema<>(ComposerControls.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ComposerControls>>asList(new dev.openallay.value.ValueSchema.Component<>(ComposerControls.class, "input", ComposerControls::input), new dev.openallay.value.ValueSchema.Component<>(ComposerControls.class, "send", ComposerControls::send), new dev.openallay.value.ValueSchema.Component<>(ComposerControls.class, "stop", ComposerControls::stop), new dev.openallay.value.ValueSchema.Component<>(ComposerControls.class, "retry", ComposerControls::retry)), arguments -> new ComposerControls((Rect) arguments[0], (Rect) arguments[1], (Rect) arguments[2], (Rect) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(Rect.ValueSchemaProvider.class)
public static final class Rect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
public static final Rect EMPTY = new Rect(0, 0, 0, 0);
public int right() { return x + width; }
public int bottom() { return y + height; }
public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rect)) return false;
        Rect that = (Rect) other;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Rect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Rect> schema() {
            return new dev.openallay.value.ValueSchema<>(Rect.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Rect>>asList(new dev.openallay.value.ValueSchema.Component<>(Rect.class, "x", Rect::x), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "y", Rect::y), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "width", Rect::width), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "height", Rect::height)), arguments -> new Rect((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiLayout)) return false;
        GuideUiLayout that = (GuideUiLayout) other;
        return narrow == that.narrow && java.util.Objects.equals(topBar, that.topBar) && java.util.Objects.equals(sessionRail, that.sessionRail) && java.util.Objects.equals(transcript, that.transcript) && java.util.Objects.equals(progress, that.progress) && java.util.Objects.equals(composer, that.composer) && java.util.Objects.equals(detail, that.detail) && detailOverlay == that.detailOverlay && java.util.Objects.equals(telemetry, that.telemetry) && java.util.Objects.equals(header, that.header) && java.util.Objects.equals(composerControls, that.composerControls);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(narrow);
        hash = 31 * hash + java.util.Objects.hashCode(topBar);
        hash = 31 * hash + java.util.Objects.hashCode(sessionRail);
        hash = 31 * hash + java.util.Objects.hashCode(transcript);
        hash = 31 * hash + java.util.Objects.hashCode(progress);
        hash = 31 * hash + java.util.Objects.hashCode(composer);
        hash = 31 * hash + java.util.Objects.hashCode(detail);
        hash = 31 * hash + Boolean.hashCode(detailOverlay);
        hash = 31 * hash + java.util.Objects.hashCode(telemetry);
        hash = 31 * hash + java.util.Objects.hashCode(header);
        hash = 31 * hash + java.util.Objects.hashCode(composerControls);
        return hash;
    }
    @Override public String toString() { return "GuideUiLayout[narrow=" + narrow + ", topBar=" + topBar + ", sessionRail=" + sessionRail + ", transcript=" + transcript + ", progress=" + progress + ", composer=" + composer + ", detail=" + detail + ", detailOverlay=" + detailOverlay + ", telemetry=" + telemetry + ", header=" + header + ", composerControls=" + composerControls + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "narrow", GuideUiLayout::narrow), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "topBar", GuideUiLayout::topBar), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "sessionRail", GuideUiLayout::sessionRail), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "transcript", GuideUiLayout::transcript), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "progress", GuideUiLayout::progress), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "composer", GuideUiLayout::composer), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "detail", GuideUiLayout::detail), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "detailOverlay", GuideUiLayout::detailOverlay), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "telemetry", GuideUiLayout::telemetry), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "header", GuideUiLayout::header), new dev.openallay.value.ValueSchema.Component<>(GuideUiLayout.class, "composerControls", GuideUiLayout::composerControls)), arguments -> new GuideUiLayout((Boolean) arguments[0], (Rect) arguments[1], (Rect) arguments[2], (Rect) arguments[3], (Rect) arguments[4], (Rect) arguments[5], (Rect) arguments[6], (Boolean) arguments[7], (Rect) arguments[8], (Header) arguments[9], (ComposerControls) arguments[10]));
        }
    }
}
