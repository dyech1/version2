package com.colorwheel;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Random;

public class ColorWheelMod implements ModInitializer {
    public static final String MOD_ID = "colorwheel";
    public static final Identifier SPIN_ID = Identifier.of(MOD_ID, "spin");
    public static final SoundEvent SPIN_SOUND = SoundEvent.of(SPIN_ID);

    /** Nivel de permiso necesario (2 = operador/admin). */
    public static final int PERMISO_ADMIN = 2;
    /** Duración del giro en milisegundos (el sonido dura ~13 s). */
    public static final int SPIN_MS = 12_000;

    private static final Random RANDOM = new Random();
    private static int ticksLeft = 0;
    private static int activeIndex = -1;
    private static String activeMessage = "";
    private static final int MAX_MENSAJE = 200;

    @Override
    public void onInitialize() {
        Registry.register(Registries.SOUND_EVENT, SPIN_ID, SPIN_SOUND);

        PayloadTypeRegistry.playS2C().register(OpenMenuPayload.ID, OpenMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinPayload.ID, SpinPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpinRequestPayload.ID, SpinRequestPayload.CODEC);

        // /ruleta: solo la ven y la usan los admins (nivel de permiso 2)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ruleta")
                        .requires(src -> src.hasPermissionLevel(PERMISO_ADMIN))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            if (ServerPlayNetworking.canSend(player, OpenMenuPayload.ID)) {
                                ServerPlayNetworking.send(player, new OpenMenuPayload());
                            }
                            return 1;
                        })));

        // El admin eligió un color en el menú
        ServerPlayNetworking.registerGlobalReceiver(SpinRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // Se valida en el servidor, así nadie puede saltarse el permiso
            if (!player.hasPermissionLevel(PERMISO_ADMIN)) return;
            int i = payload.index();
            if (i < 0 || i >= WheelColor.COUNT) return;
            if (ticksLeft > 0) {
                player.sendMessage(Text.literal("La ruleta ya está girando.").formatted(Formatting.RED), true);
                return;
            }
            MinecraftServer server = context.server();
            String msg = payload.message() == null ? "" : payload.message().trim();
            if (msg.length() > MAX_MENSAJE) msg = msg.substring(0, MAX_MENSAJE);
            activeMessage = msg;
            activeIndex = i;
            ticksLeft = SPIN_MS / 50;
            float jitter = (RANDOM.nextFloat() - 0.5f) * 28f; // desvío dentro del segmento
            SpinPayload spin = new SpinPayload(i, jitter, SPIN_MS);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(p, SpinPayload.ID)) {
                    ServerPlayNetworking.send(p, spin);
                }
            }
        });

        // Cuando termina el giro, el servidor avisa a todos
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ticksLeft > 0 && --ticksLeft == 0) {
                alTerminar(server, WheelColor.values()[activeIndex], activeMessage);
                activeIndex = -1;
                activeMessage = "";
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { ticksLeft = 0; activeIndex = -1; activeMessage = ""; });
    }

    /** Se ejecuta en el servidor cuando la ruleta se detiene. Aquí puedes agregar premios o efectos. */
    private static void alTerminar(MinecraftServer server, WheelColor color, String mensaje) {
        server.getPlayerManager().broadcast(
                Text.literal("¡Salió ").formatted(Formatting.GOLD)
                        .append(Text.literal(color.nombre).styled(s -> s.withColor(color.rgb).withBold(true)))
                        .append(Text.literal("!").formatted(Formatting.GOLD)),
                false);
        if (!mensaje.isEmpty()) {
            // Se permiten códigos de color con & (por ejemplo &a = verde, &l = negrita)
            server.getPlayerManager().broadcast(Text.literal(mensaje.replaceAll("&([0-9a-fk-or])", "\u00a7$1")), false);
        }
    }
}
