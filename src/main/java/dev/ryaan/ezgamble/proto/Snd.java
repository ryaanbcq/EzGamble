package dev.ryaan.ezgamble.proto;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** The prototype's play(name, pitch, vol): the mod's sounds (assets/ezgamble/sounds), and mc_* = vanilla SoundEvents. */
public final class Snd {
    private Snd() {}

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static final String[] OURS = {"pay", "cash", "fail", "open", "close",
            "ui_cards_close", "ui_cards_open", "ui_chips_close", "ui_chips_open", "ui_coin_close", "ui_coin_open", "ui_double_close", "ui_double_open",
            "ui_jackpot_close", "ui_jackpot_open", "ui_kcards_close", "ui_kcards_open", "ui_kcollide_close", "ui_kcollide_open", "ui_khandle_close",
            "ui_khandle_open", "ui_kstack_close", "ui_kstack_open", "ui_pop_close", "ui_pop_open", "ui_tick_close", "ui_tick_open", "seg_select", "seg_tick", "btn_press"};

    /** Called once from the mod initializer. */
    public static void register() {
        for (String n : OURS) {
            Identifier id = Identifier.of("ezgamble", n);
            EVENTS.put(n, Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id)));
        }
        EVENTS.put("mc_sweep", SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
        EVENTS.put("mc_sweep2", SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
        EVENTS.put("mc_netherite", SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value());
        EVENTS.put("mc_totem", SoundEvents.ITEM_TOTEM_USE);
        EVENTS.put("mc_explode", SoundEvents.ENTITY_GENERIC_EXPLODE.value());
        EVENTS.put("mc_pling", SoundEvents.BLOCK_NOTE_BLOCK_PLING.value());
        EVENTS.put("mc_orb", SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
        EVENTS.put("mc_horn", SoundEvents.GOAT_HORN_SOUNDS.get(0).value());
        EVENTS.put("mc_mace", SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY);
        EVENTS.put("mc_crossbow", SoundEvents.ITEM_CROSSBOW_SHOOT);
        EVENTS.put("mc_trident", SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value());
        EVENTS.put("mc_spear", SoundEvents.ITEM_SPEAR_LUNGE_1.value());
        EVENTS.put("mc_bell", SoundEvents.BLOCK_BELL_USE);
        EVENTS.put("mc_thunder", SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
        EVENTS.put("mc_rain", SoundEvents.WEATHER_RAIN);
        EVENTS.put("mc_chime", SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
    }

    public static SoundEvent event(String name) { return EVENTS.get(name); }

    public static void play(String name) { play(name, 1, 1); }
    public static void play(String name, double pitch) { play(name, pitch, 1); }

    public static void play(String name, double pitch, double vol) {
        // the menu's open / close: the Kenney chips the prototype settled on, never louder than 0.55
        if (name.equals("open") || name.equals("close")) { name = "ui_kcollide_" + name; pitch = 1; vol = Math.min(vol, 0.55); }
        if (name.equals("click")) { name = "btn_press"; vol = Math.min(vol, 0.7); }   // our own press, never the vanilla button click (Ryan: too harsh)
        SoundEvent e = EVENTS.get(name);
        if (e == null) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(e, (float) pitch, (float) Math.max(0, Math.min(1, vol))));
    }
}
