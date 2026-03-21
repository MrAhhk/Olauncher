package app.olauncher.reflection

/** Shared timing and layout for reflection sheet + untick pause + setup dialogs. */
object ReflectionConstants {
    /** Buttons stay disabled until this elapses (matches [ReflectionSheet]). */
    const val PROMPT_BUTTON_DELAY_MS = 6000L

    const val DIALOG_WIDTH_FRACTION_MAIN = 0.92f
    const val DIALOG_WIDTH_FRACTION_UNTICK = 0.85f

    const val ALPHABET_STRIP_TEXT_SP = 10f
    const val DISABLED_CONTROL_ALPHA = 0.3f
    const val INACTIVE_LETTER_ALPHA = 0.35f
}
