package dev.ryaan.ezgamble.proto;

import org.lwjgl.glfw.GLFW;

/**
 * The prototype's KeyboardEvent: key = "Escape", "Enter", "Backspace", "ArrowUp"..., or the typed character
 * (one char) for text. Built from keyPressed (special keys, and letters for shortcuts) and charTyped (text).
 */
public record Key(String key, boolean typed, boolean shift, boolean ctrl) {
    public static Key fromCode(int code, int mods) {
        String k = switch (code) {
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_UP -> "ArrowUp";
            case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
            case GLFW.GLFW_KEY_LEFT -> "ArrowLeft";
            case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
            case GLFW.GLFW_KEY_SPACE -> " ";
            default -> {
                String n = GLFW.glfwGetKeyName(code, 0);
                yield n != null && n.length() == 1 ? n : "";
            }
        };
        return new Key(k, false, (mods & GLFW.GLFW_MOD_SHIFT) != 0, (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0);
    }

    public static Key typed(String ch) { return new Key(ch, true, false, false); }

    /** A printable character (a typed one). */
    public boolean isChar() { return typed && key.length() >= 1; }
    public boolean is(String k) { return key.equalsIgnoreCase(k); }
}
