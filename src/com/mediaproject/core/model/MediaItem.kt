package com.mediaproject.core.model

enum class Status(val label: String, val emoji: String) {
    WISHLIST("보고싶다", "[ ]"), IN_PROGRESS("진행중", "[>]"), DONE("완료", "[V]")
}

sealed class MediaItem {
    abstract val id: Int; abstract val title: String; abstract val genre: String
    abstract val rating: Double; abstract val date: String; abstract val memo: String
    abstract val status: Status; abstract val isFavorite: Boolean; abstract val tags: List<String>

    fun matches(k: String) = (listOf(title, genre, memo) + tags).any { it.contains(k, true) }
    abstract fun toCsv(): String
    abstract fun toDisplayString(): String
    abstract fun typeName(): String

    fun withStatus(newStatus: Status): MediaItem = when (this) {
        is Book -> this.copy(status = newStatus)
        is Movie -> this.copy(status = newStatus)
        is Music -> this.copy(status = newStatus)
    }

    fun withFavorite(newFavorite: Boolean): MediaItem = when (this) {
        is Book -> this.copy(isFavorite = newFavorite)
        is Movie -> this.copy(isFavorite = newFavorite)
        is Music -> this.copy(isFavorite = newFavorite)
    }

    fun withMemoAndTags(newMemo: String, newTags: List<String>): MediaItem = when (this) {
        is Book -> this.copy(memo = newMemo, tags = newTags)
        is Movie -> this.copy(memo = newMemo, tags = newTags)
        is Music -> this.copy(memo = newMemo, tags = newTags)
    }

    fun withBaseDetails(newTitle: String, newGenre: String, newRating: Double, newDate: String, newMemo: String, newTags: List<String>): MediaItem = when (this) {
        is Book -> this.copy(title = newTitle, genre = newGenre, rating = newRating, date = newDate, memo = newMemo, tags = newTags)
        is Movie -> this.copy(title = newTitle, genre = newGenre, rating = newRating, date = newDate, memo = newMemo, tags = newTags)
        is Music -> this.copy(title = newTitle, genre = newGenre, rating = newRating, date = newDate, memo = newMemo, tags = newTags)
    }
}

data class Book(override val id: Int, override val title: String, override val genre: String, override val rating: Double, override val date: String, override val memo: String, override val status: Status, override val isFavorite: Boolean, override val tags: List<String>, val author: String, val publisher: String, val pages: Int) : MediaItem() {
    override fun typeName() = "BOOK"
    override fun toCsv() = "BOOK|$id|$title|$genre|$rating|$date|$memo|${status.name}|$isFavorite|${tags.joinToString(",")}|$author|$publisher|$pages"
    override fun toDisplayString() = "[책] #$id $title | $author 저 | 평점: $rating | $status"
}

data class Movie(override val id: Int, override val title: String, override val genre: String, override val rating: Double, override val date: String, override val memo: String, override val status: Status, override val isFavorite: Boolean, override val tags: List<String>, val director: String, val runningTime: Int, val ageRating: String) : MediaItem() {
    override fun typeName() = "MOVIE"
    override fun toCsv() = "MOVIE|$id|$title|$genre|$rating|$date|$memo|${status.name}|$isFavorite|${tags.joinToString(",")}|$director|$runningTime|$ageRating"
    override fun toDisplayString() = "[영화] #$id $title | $director 감독 | 평점: $rating | $status"
}

data class Music(override val id: Int, override val title: String, override val genre: String, override val rating: Double, override val date: String, override val memo: String, override val status: Status, override val isFavorite: Boolean, override val tags: List<String>, val artist: String, val album: String, val trackCount: Int) : MediaItem() {
    override fun typeName() = "MUSIC"
    override fun toCsv() = "MUSIC|$id|$title|$genre|$rating|$date|$memo|${status.name}|$isFavorite|${tags.joinToString(",")}|$artist|$album|$trackCount"
    override fun toDisplayString() = "[음악] #$id $title | $artist 아티스트 | 평점: $rating | $status"
}