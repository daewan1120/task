package com.mediaproject.core.library

import com.mediaproject.core.model.*
import com.mediaproject.core.repo.MediaRepository

class MediaLibrary(private val repo: MediaRepository) {
    // 앱이 시작될 때 로드된 데이터를 메모리에 유지
    private var _items = repo.load().toMutableList()

    // 외부에 공개되는 읽기 전용 리스트
    val items: List<MediaItem> get() = _items

    /**
     * 항목 추가: 메모리와 파일에 즉시 반영
     */
    fun add(item: MediaItem) {
        _items.add(item)
        repo.append(item)
    }

    /**
     * 항목 삭제: ID 기반으로 제거 후 파일 업데이트
     */
    fun remove(id: Int) {
        if (_items.removeIf { it.id == id }) {
            repo.deleteItem(id)
        }
    }

    /**
     * 항목 수정: 람다식을 사용하여 데이터 변환 후 파일 업데이트
     */
    fun update(id: Int, transform: (MediaItem) -> MediaItem) {
        val index = _items.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = transform(_items[index])
            _items[index] = updated
            repo.updateItem(updated)
        }
    }

    /**
     * 전체 데이터 통계 계산
     */
    fun statistics(): String {
        if (_items.isEmpty()) return "등록된 데이터가 없습니다."

        val total = _items.size
        val avgRating = _items.map { it.rating }.average()
        val favCount = _items.count { it.isFavorite }

        return """
            [라이브러리 통계]
            - 전체 항목 수: ${total}개
            - 평균 평점: ${"%.2f".format(avgRating)}점
            - 즐겨찾기 항목 수: ${favCount}개
        """.trimIndent()
    }

    /**
     * 특정 조건에 맞는 데이터 일괄 변경
     */
    fun bulkUpdate(predicate: (MediaItem) -> Boolean, transform: (MediaItem) -> MediaItem) {
        _items.forEachIndexed { index, item ->
            if (predicate(item)) {
                val updated = transform(item)
                _items[index] = updated
                repo.updateItem(updated)
            }
        }
    }

    /**
     * ID로 항목 검색
     */
    fun findById(id: Int): MediaItem? = _items.find { it.id == id }

    /**
     * 키워드로 검색 (제목, 장르, 메모, 태그 포함)
     */
    fun search(keyword: String): List<MediaItem> {
        return _items.filter { it.matches(keyword) }
    }
}