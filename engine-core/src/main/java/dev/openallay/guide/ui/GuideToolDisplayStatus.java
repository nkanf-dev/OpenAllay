package dev.openallay.guide.ui;

import dev.openallay.guide.GuideToolStatus;

/** Derived UI state only; never persisted or sent through the Tool protocol. */
public enum GuideToolDisplayStatus {
    RUNNING, SUCCEEDED, FAILED, NO_RESULT_RECORDED;

    public static GuideToolDisplayStatus from(GuideToolStatus actual, boolean requestTerminal) {
        if (requestTerminal && actual == GuideToolStatus.RUNNING) return NO_RESULT_RECORDED;
        {
dev.openallay.guide.ui.GuideToolDisplayStatus $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((actual)) {
case RUNNING:
{
$oaSwitch0_exit_result = RUNNING; break $oaSwitch0_exit;
}
case SUCCEEDED:
{
$oaSwitch0_exit_result = SUCCEEDED; break $oaSwitch0_exit;
}
case FAILED:
{
$oaSwitch0_exit_result = FAILED; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }

    public String translationKey() {
        return "screen.openallay.detail.tool.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
