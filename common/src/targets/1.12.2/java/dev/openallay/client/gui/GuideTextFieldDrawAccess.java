package dev.openallay.client.gui;

/** Exact native text draw projection; editor selection and cursor remain vanilla-owned. */
public interface GuideTextFieldDrawAccess {
    String openallay$drawText();
    void openallay$drawText(String text);
}
