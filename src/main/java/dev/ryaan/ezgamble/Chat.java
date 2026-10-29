package dev.ryaan.ezgamble;
import dev.ryaan.ezgamble.ui.Glass;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Chat lines in the mod's look: small caps prefix in the lavender to pink gradient, soft grey body. */
public final class Chat {
    private Chat() {}

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
    public static final int GREY = 0xA79FB8, DIM = 0x7D7590, WHITE = 0xFFFFFF, GREEN = 0x4BE07A, RED = 0xFF5C5C;

    public static String smallCaps(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            char l = Character.toLowerCase(c);
            int i = LOWER.indexOf(l);
            b.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return b.toString();
    }

    /** Each character coloured along the lavender to pink gradient. */
    public static MutableText gradient(String s) {
        MutableText t = Text.empty();
        int n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            int c = Glass.lerp(Glass.LAV, Glass.PINK, (float) i / n) & 0xFFFFFF;
            t.append(Text.literal(String.valueOf(s.charAt(i))).withColor(c));
        }
        return t;
    }

    public static MutableText prefix() {
        return gradient(smallCaps("ezgamble")).append(Text.literal(" ").withColor(DIM));
    }

    public static MutableText grey(String s) { return Text.literal(s).withColor(GREY); }
    public static MutableText dim(String s) { return Text.literal(s).withColor(DIM); }
    public static MutableText white(String s) { return Text.literal(s).withColor(WHITE); }
}
