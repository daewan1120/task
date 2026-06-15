package com.mediaproject.core.repo

import com.mediaproject.core.model.*
import java.io.File

class MediaRepository(private val filePath: String = "data.csv") {
    private val file = File(filePath)

    // 1. 전체 데이터 로드 (앱 시작 시 호출)
    fun load(): List<MediaItem> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.mapNotNull { parseLine(it) }
    }

    // 2. 새로운 데이터 추가 (전체 덮어쓰기 대신 append 모드 활용 가능)
    fun append(item: MediaItem) {
        file.appendText(item.toCsv() + "\n")
    }

    // 3. 특정 라인 수정 (Update-in-place)
    fun updateItem(updatedItem: MediaItem) {
        val lines = file.readLines().toMutableList()
        val index = lines.indexOfFirst { it.startsWith(updatedItem.typeName() + "|" + updatedItem.id + "|") }
        if (index != -1) {
            lines[index] = updatedItem.toCsv()
            file.writeText(lines.joinToString("\n") + "\n")
        }
    }

    // 4. 데이터 삭제
    fun deleteItem(id: Int) {
        val lines = file.readLines().filter { !it.contains("|$id|") }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    fun parseLine(line: String): MediaItem? {
        val p = line.split("|")
        return try {
            when (p[0]) {
                "BOOK" -> Book(p[1].toInt(), p[2], p[3], p[4].toDouble(), p[5], p[6], Status.valueOf(p[7]), p[8].toBoolean(), p[9].split(","), p[10], p[11], p[12].toInt())
                "MOVIE" -> Movie(p[1].toInt(), p[2], p[3], p[4].toDouble(), p[5], p[6], Status.valueOf(p[7]), p[8].toBoolean(), p[9].split(","), p[10], p[11].toInt(), p[12])
                "MUSIC" -> Music(p[1].toInt(), p[2], p[3], p[4].toDouble(), p[5], p[6], Status.valueOf(p[7]), p[8].toBoolean(), p[9].split(","), p[10], p[11], p[12].toInt())
                else -> null
            }
        } catch (e: Exception) { null }
    }
}