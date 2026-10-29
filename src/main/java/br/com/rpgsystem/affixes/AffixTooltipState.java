package br.com.rpgsystem.affixes;

/** Estado transitório usado pelo cliente enquanto o tooltip está sendo montado. */
public final class AffixTooltipState {
	private static final ThreadLocal<Boolean> SHIFT_DOWN = ThreadLocal.withInitial(() -> false);

	private AffixTooltipState() {}

	public static void setShiftDown(boolean value) { SHIFT_DOWN.set(value); }
	public static boolean isShiftDown() { return SHIFT_DOWN.get(); }
}
