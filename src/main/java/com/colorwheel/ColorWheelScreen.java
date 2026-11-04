package com.colorwheel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menú del admin: elige el color, escribe el mensaje del chat y pulsa GIRAR. */
public class ColorWheelScreen extends Screen {
    // Se recuerda el último mensaje escrito mientras el juego siga abierto
    private static String lastMessage = "";
    private static int lastSelected = 0;

    private final ButtonWidget[] colorButtons = new ButtonWidget[WheelColor.COUNT];
    private TextFieldWidget messageField;
    private int selected = lastSelected;

    public ColorWheelScreen() {
        super(Text.literal("Ruleta de colores"));
    }

    @Override
    protected void init() {
        int w = 100, gap = 8;
        int x0 = this.width / 2 - (w * 2 + gap) / 2;
        int top = this.height / 2 - 76;

        // 8 colores en 2 columnas
        for (int i = 0; i < WheelColor.COUNT; i++) {
            final int idx = i;
            int col = i / 4, row = i % 4;
            colorButtons[i] = ButtonWidget.builder(Text.empty(), b -> { selected = idx; refresh(); })
                    .dimensions(x0 + col * (w + gap), top + row * 22, w, 20).build();
            addDrawableChild(colorButtons[i]);
        }

        int fieldY = top + 4 * 22 + 18;
        messageField = new TextFieldWidget(this.textRenderer, x0, fieldY, w * 2 + gap, 20, Text.literal("Mensaje"));
        messageField.setMaxLength(200);
        messageField.setPlaceholder(Text.literal("Mensaje para el chat (opcional)"));
        messageField.setText(lastMessage);
        addDrawableChild(messageField);

        addDrawableChild(ButtonWidget.builder(Text.literal("¡GIRAR!").formatted(Formatting.BOLD), b -> {
            lastMessage = messageField.getText();
            lastSelected = selected;
            ClientPlayNetworking.send(new SpinRequestPayload(selected, messageField.getText()));
            this.close();
        }).dimensions(x0, fieldY + 28, w * 2 + gap, 24).build());
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < WheelColor.COUNT; i++) {
            WheelColor c = WheelColor.values()[i];
            String prefix = (i == selected) ? "» " : "";
            colorButtons[i].setMessage(Text.literal(prefix + c.nombre).styled(s -> s.withColor(c.rgb)));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = 100, gap = 8;
        int x0 = this.width / 2 - (w * 2 + gap) / 2;
        int top = this.height / 2 - 76;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top - 16, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Texto para el chat al terminar:"), x0, top + 4 * 22 + 6, 0xA0A0A0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
