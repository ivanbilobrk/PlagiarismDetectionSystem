package com.fer.projekt.languages

import de.jplag.text.NaturalLanguage

class CustomNaturalLanguage(
    private val suffixes: List<String>
): NaturalLanguage() {

    override fun suffixes(): Array<String> {
        val setOfSuffixes = super.suffixes().toSet() + suffixes.toSet()
        return setOfSuffixes.toTypedArray()
    }
}
