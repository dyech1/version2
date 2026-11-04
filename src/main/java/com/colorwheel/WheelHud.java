package com.colorwheel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;

/** La ruleta que todos los jugadores ven girar encima de su pantalla. */
public final class WheelHud {
    private static final Identifier WHEEL = tex("wheel");
    private static final Identifier TINT = tex("tint");
    private static final Identifier GLOW = tex("glow");
    private static final Identifier POINTER = tex("pointer");

    private static final int RADIUS = 64;
    private static final int FULL_TURNS = 6;
    private static final long APPEAR_MS = 600;  // aparece con un pequeño "pop"
    private static final long FLASH_MS = 700;   // destello de color al detenerse
    private static final long HOLD_MS = 4500;   // cuánto se queda visible al terminar
    private static final long FADE_MS = 700;

    private static float savedAngle = 0f;
    private static boolean visible, spinning;
    private static long startMs, durationMs, endMs;
    private static float startAngle, targetAngle, angle;
    private static int selected;

    private WheelHud() {}

    private static Identifier tex(String n) {
        return Identifier.of(ColorWheelMod.MOD_ID, "textures/gui/" + n + ".png");
    }

    public static void start(int index, float jitter, int duration) {
        MinecraftClient mc = MinecraftClient.getInstance();
        selected = index;
        startAngle = savedAngle;
        angle = savedAngle;
        float needed = mod(-index * 45f + jitter - startAngle, 360f);
        targetAngle = startAngle + FULL_TURNS * 360f + needed;
        durationMs = duration;
        startMs = Util.getMeasuringTimeMs();
        spinning = true;
        visible = true;
        mc.getSoundManager().play(PositionedSoundInstance.master(ColorWheelMod.SPIN_SOUND, 1.0f));
    }

    private static float mod(float a, float m) {
        return ((a % m) + m) % m;
    }

    private static void drawTex(DrawContext ctx, Identifier tex, int cx, int cy, float deg, float scale,
                                float r, float g, float b, float a) {
        if (a <= 0.004f) return;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg));
        m.scale(scale, scale, 1f);
        RenderSystem.setShaderColor(r, g, b, a);
        ctx.drawTexture(tex, -RADIUS, -RADIUS, RADIUS * 2, RADIUS * 2, 0, 0, 256, 256, 256, 256);
        m.pop();
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!visible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        long now = Util.getMeasuringTimeMs();
        float alpha = 1f, scale = 1f, tint = 0f, glow = 0f, speed = 0f; // speed en grados por ms

        if (spinning) {
            float t = (now - startMs) / (float) durationMs;
            if (t >= 1f) {
                spinning = false;
                savedAngle = mod(targetAngle, 360f);
                angle = savedAngle;
                endMs = now;
            } else {
                float inv = 1f - t;
                angle = startAngle + (targetAngle - startAngle) * (1f - inv * inv * inv); // frena poco a poco
                speed = (targetAngle - startAngle) * 3f * inv * inv / durationMs;
            }
        }
        if (!spinning) {
            long since = now - endMs;
            if (since > HOLD_MS + FADE_MS) {
                visible = false;
                return;
            }
            float k = Math.min(1f, since / (float) FLASH_MS);
            float ke = 1f - (1f - k) * (1f - k);
            scale = 1f + 0.18f * ke;           // la ruleta crece un poco al terminar
            tint = 0.88f * ke;                  // todos los segmentos toman el color ganador
            glow = k < 1f ? k : 1f - 0.5f * Math.min(1f, (since - FLASH_MS) / 600f);
            if (since > HOLD_MS) alpha = 1f - (since - HOLD_MS) / (float) FADE_MS;
        }
        float ap = Math.min(1f, (now - startMs) / (float) APPEAR_MS);
        float apE = 1f - (1f - ap) * (1f - ap);
        alpha *= apE;
        scale *= 0.7f + 0.3f * apE;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = 118;
        WheelColor win = WheelColor.values()[selected];
        float wr = ((win.rgb >> 16) & 255) / 255f, wg = ((win.rgb >> 8) & 255) / 255f, wb = (win.rgb & 255) / 255f;

        RenderSystem.enableBlend();

        // Estela de movimiento mientras gira rápido
        float step = speed * 18f;
        if (spinning && step > 1.5f) {
            drawTex(ctx, WHEEL, cx, cy, angle - step * 2f, scale, 1f, 1f, 1f, 0.16f * alpha);
            drawTex(ctx, WHEEL, cx, cy, angle - step, scale, 1f, 1f, 1f, 0.28f * alpha);
        }
        drawTex(ctx, WHEEL, cx, cy, angle, scale, 1f, 1f, 1f, alpha);
        drawTex(ctx, TINT, cx, cy, angle, scale, wr, wg, wb, tint * alpha);
        drawTex(ctx, GLOW, cx, cy, 0f, scale, 1f, 1f, 1f, glow * alpha);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Puntero: la gema toma el color del segmento que tiene debajo
        int rs = (int) (RADIUS * scale);
        int px = cx - 20, py = cy - rs - 38;
        int under = Math.round(mod(-angle, 360f) / 45f) % WheelColor.COUNT;
        int a255 = Math.max(4, (int) (alpha * 255)) << 24;
        ctx.fill(cx - 4, py + 12, cx + 4, py + 20, a255 | WheelColor.values()[under].rgb);
        ctx.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(POINTER, px, py, 40, 50, 0, 0, 40, 50, 40, 50);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (!spinning) {
            Text txt = Text.literal("¡Salió " + win.nombre + "!").styled(s -> s.withBold(true));
            ctx.drawCenteredTextWithShadow(mc.textRenderer, txt, cx, cy + rs + 8, a255 | win.rgb);
        }
    }
}
