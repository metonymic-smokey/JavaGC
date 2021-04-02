package at.jku.anttracks.gui.model

import javafx.scene.text.Text

class Description(text: String = "") {
    val styledDescriptionParts = mutableListOf(Pair(Idea.Companion.Style.DEFAULT, text))
    val asString by lazy {
        styledDescriptionParts.joinToString("") { it.second }
    }

    fun appendDefault(text: String): Description {
        styledDescriptionParts.add(Pair(Idea.Companion.Style.DEFAULT, text))
        return this
    }

    fun appendEmphasized(text: String): Description {
        styledDescriptionParts.add(Pair(Idea.Companion.Style.EMPHASIZE, text))
        return this
    }

    fun appendCode(text: String): Description {
        styledDescriptionParts.add(Pair(Idea.Companion.Style.CODE, text))
        return this
    }

    fun linebreak(): Description {
        styledDescriptionParts.add(Pair(Idea.Companion.Style.DEFAULT, System.lineSeparator()))
        return this
    }

    fun concat(otherDesc: Description): Description {
        this.styledDescriptionParts += otherDesc.styledDescriptionParts
        return this
    }

    override fun toString() = asString

    fun toTextNodes() =
            styledDescriptionParts.map { formattedDescriptionPart ->
                val text = Text(formattedDescriptionPart.second)
                text.styleClass.add(formattedDescriptionPart.first.styleClass)
                text
            }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (toString() != other.toString()) return false
        return true
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    // Shortcuts
    infix fun a(s: String): Description {
        appendDefault(s)
        return this
    }

    infix fun e(s: String): Description {
        appendEmphasized(s)
        return this
    }

    infix fun c(s: String): Description {
        appendCode(s)
        return this
    }

    fun ln(): Description {
        linebreak()
        return this
    }

    operator fun plus(otherDesc: Description) {
        concat(otherDesc)
    }
}