package com.colorwheel;

/** Los 8 colores de la ruleta, en el orden en que aparecen (sentido horario desde arriba). */
public enum WheelColor {
    ROJO("Rojo", 0xE61E32),
    ROSA("Rosa", 0xEB32C8),
    MORADO("Morado", 0x8228DC),
    AZUL("Azul", 0x2832DC),
    CELESTE("Celeste", 0x1EDCE6),
    VERDE("Verde", 0x3CDC3C),
    AMARILLO("Amarillo", 0xFFE11E),
    NARANJA("Naranja", 0xFF9614);

    public final String nombre;
    public final int rgb;

    WheelColor(String nombre, int rgb) {
        this.nombre = nombre;
        this.rgb = rgb;
    }

    public static final int COUNT = values().length;
}
