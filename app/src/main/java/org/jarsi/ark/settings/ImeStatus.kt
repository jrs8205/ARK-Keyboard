package org.jarsi.ark.settings

/** Näppäimistön käyttöönoton tila asetussivun tilakorttia varten. */
enum class ImeStatus {
    /** Näppäimistöä ei ole sallittu järjestelmän asetuksissa. */
    NOT_ENABLED,

    /** Sallittu, mutta jokin muu näppäimistö on valittuna. */
    NOT_SELECTED,

    /** Sallittu ja valittu käytössä olevaksi näppäimistöksi. */
    READY;

    companion object {
        /** Valinta ilman käyttöönottoa ei ole mahdollinen tila; se luetaan "ei käytössä". */
        fun of(enabled: Boolean, selected: Boolean): ImeStatus = when {
            !enabled -> NOT_ENABLED
            !selected -> NOT_SELECTED
            else -> READY
        }
    }
}
