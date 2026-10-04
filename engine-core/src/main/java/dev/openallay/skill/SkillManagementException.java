package dev.openallay.skill;

public final class SkillManagementException extends RuntimeException {
    private final String code;

    public SkillManagementException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SkillManagementException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
