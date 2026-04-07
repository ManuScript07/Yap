package com.example.yap.util.extension

// Точная проверка: только эмодзи и спец-символы, никаких букв/цифр
import java.text.BreakIterator

// Считаем "визуальные" символы (Grapheme Clusters)
fun String.countGraphemeClusters(): Int {
    val it = BreakIterator.getCharacterInstance()
    it.setText(this)
    var count = 0
    while (it.next() != BreakIterator.DONE) {
        count++
    }
    return count
}

// Улучшенная проверка на эмодзи
fun String.isEmojiOnly(): Boolean {
    if (this.isBlank()) return false
    return this.all { char ->
        val type = Character.getType(char).toByte()
        // Variation Selectors, Surrogates и Symbols — это наши друзья
        val isEmojiPart = char.isSurrogate() ||
                type == Character.OTHER_SYMBOL ||
                type == Character.NON_SPACING_MARK ||
                type == Character.SURROGATE ||
                char.code in 0xFE00..0xFE0F // Variation Selectors

        isEmojiPart && !Character.isLetterOrDigit(char)
    }
}
