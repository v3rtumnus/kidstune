package at.kidstune.kids.domain.model

enum class BrowseCategory {
    MUSIC, FAVORITES;

    companion object {
        fun fromString(value: String): BrowseCategory =
            entries.firstOrNull { it.name == value } ?: MUSIC
    }
}
