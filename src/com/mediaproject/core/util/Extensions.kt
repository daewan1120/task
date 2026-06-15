package com.mediaproject.core.util

import com.mediaproject.core.model.MediaItem
import com.mediaproject.core.model.Status

fun List<MediaItem>.filterByGenre(genre: String?) =
    if (genre.isNullOrBlank()) this else filter { it.genre.contains(genre, ignoreCase = true) }

fun List<MediaItem>.filterByStatus(status: Status?) =
    if (status == null) this else filter { it.status == status }

fun List<MediaItem>.search(keyword: String) =
    filter { it.matches(keyword) }

fun List<MediaItem>.topN(n: Int) =
    sortedByDescending { it.rating }.take(n)

fun List<MediaItem>.sortByOption(option: String): List<MediaItem> = when (option) {
    "1" -> sortedBy { it.id }
    "2" -> sortedBy { it.title }
    "3" -> sortedByDescending { it.rating }
    "4" -> sortedBy { it.date }
    else -> this
}
