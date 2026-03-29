package com.example.yap.ui.util

fun String.isEmojiOnly(): Boolean {
    if (this.isBlank()) return false
    return this.all { char ->
        val type = Character.getType(char).toByte()
        // 1. Проверяем на суррогатные пары (большинство современных эмодзи)
        // 2. Проверяем на графические символы (сердца, значки и т.д.)
        // 3. Исключаем буквы и цифры (чтобы "Да", "Го" и "100" не считались эмодзи)
        (char.isSurrogate() ||
                type == Character.SURROGATE ||
                type == Character.OTHER_SYMBOL ||
                type == Character.NON_SPACING_MARK) && !char.isLetterOrDigit()
    }
}