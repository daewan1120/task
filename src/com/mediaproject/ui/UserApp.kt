package com.mediaproject.ui

import com.mediaproject.core.net.NetworkClient
import com.mediaproject.core.repo.AppResult
import com.mediaproject.core.util.*
import com.mediaproject.core.model.MediaItem

class UserApp(private val client: NetworkClient) {

    fun run() {
        var running = true
        while (running) {
            clearScreen()
            println("${ANSI.BOLD}${ANSI.CYAN}┌──────────────────────────────────────────────┐${ANSI.RESET}")
            println("${ANSI.BOLD}${ANSI.CYAN}│               사용자 라이브러리 모드         │${ANSI.RESET}")
            println("${ANSI.BOLD}${ANSI.CYAN}└──────────────────────────────────────────────┘${ANSI.RESET}")
            println(" 1) 전체 목록 조회 및 정렬")
            println(" 2) 키워드 검색")
            println(" 3) 평점 필터링")
            println(" 4) 즐겨찾기(Favorite) 등록/해제")
            println(" 5) 라이브러리 요약 통계")
            println(" 0) 이전으로 (종료)")
            print("\n>> 선택 > ")

            when (readLine()?.trim()) {
                "1" -> viewAllItems()
                "2" -> searchItems()
                "3" -> filterByRating()
                "4" -> toggleFavorite()
                "5" -> showStatistics()
                "0" -> running = false
                else -> {
                    println("\n${ANSI.RED}[오류] 잘못된 선택입니다. 다시 입력해 주세요.${ANSI.RESET}")
                    pause()
                }
            }
        }
    }

    private fun viewAllItems() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.GREEN}┌── 전체 목록 조회 ──────────────────────────┐${ANSI.RESET}")
        println(" 정렬 방법을 선택하세요:")
        println(" 1) ID 순 (기본)")
        println(" 2) 제목 순")
        println(" 3) 평점 높은 순")
        println(" 4) 날짜 순")
        println(" 0) 정렬하지 않음")
        print("\n👉 선택 > ")
        val sortChoice = readLine()?.trim() ?: "1"

        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val sorted = result.value.sortByOption(sortChoice)
                printItemsTable(sorted, "전체 목록 (정렬 적용)")
            }
            is AppResult.Failure -> {
                println("\n${ANSI.RED}[오류] 목록을 불러오지 못했습니다: ${result.message}${ANSI.RESET}")
            }
        }
        pause()
    }

    private fun searchItems() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.GREEN}┌── 키워드 검색 ─────────────────────────────┐${ANSI.RESET}")
        print("검색어 입력 (제목, 장르, 메모, 태그 등) > ")
        val keyword = readLine()?.trim() ?: ""

        if (keyword.isBlank()) {
            println("\n${ANSI.RED}[오류] 검색어를 입력해 주세요.${ANSI.RESET}")
            pause()
            return
        }

        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val searchResult = result.value.search(keyword)
                printItemsTable(searchResult, "'$keyword' 검색 결과")
            }
            is AppResult.Failure -> {
                println("\n${ANSI.RED}[오류] 목록을 불러오지 못했습니다: ${result.message}${ANSI.RESET}")
            }
        }
        pause()
    }

    private fun filterByRating() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.GREEN}┌── 평점 필터링 ─────────────────────────────┐${ANSI.RESET}")
        print("최소 평점 입력 (0.0 ~ 5.0) > ")
        val minRating = readLine()?.trim()?.toDoubleOrNull()

        if (minRating == null || minRating !in 0.0..5.0) {
            println("\n${ANSI.RED}[오류] 0.0에서 5.0 사이의 숫자를 입력해 주세요.${ANSI.RESET}")
            pause()
            return
        }

        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val filtered = result.value.filter { it.rating >= minRating }
                printItemsTable(filtered, "평점 ${minRating} 이상 항목")
            }
            is AppResult.Failure -> {
                println("\n${ANSI.RED}[오류] 목록을 불러오지 못했습니다: ${result.message}${ANSI.RESET}")
            }
        }
        pause()
    }

    private fun toggleFavorite() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.GREEN}┌── 즐겨찾기(Favorite) 등록/해제 ──────────────┐${ANSI.RESET}")
        
        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val items = result.value
                if (items.isEmpty()) {
                    println("등록된 미디어 항목이 없습니다.")
                    pause()
                    return
                }
                printItemsTable(items, "미디어 목록")
                print("\n즐겨찾기를 토글할 항목 ID 입력 > ")
                val id = readLine()?.trim()?.toIntOrNull()
                if (id == null) {
                    println("\n${ANSI.RED}[오류] 올바른 ID 숫자를 입력해 주세요.${ANSI.RESET}")
                    pause()
                    return
                }

                val item = items.find { it.id == id }
                if (item == null) {
                    println("\n${ANSI.RED}[오류] 해당 ID의 항목을 찾을 수 없습니다.${ANSI.RESET}")
                    pause()
                    return
                }

                val toggled = item.withFavorite(!item.isFavorite)
                when (val updateResult = client.update(id, toggled)) {
                    is AppResult.Success -> {
                        val statusMsg = if (toggled.isFavorite) "즐겨찾기에 등록되었습니다." else "즐겨찾기에서 해제되었습니다."
                        println("\n${ANSI.GREEN}[완료] '$statusMsg'${ANSI.RESET}")
                    }
                    is AppResult.Failure -> {
                        println("\n${ANSI.RED}[오류] 업데이트 실패: ${updateResult.message}${ANSI.RESET}")
                    }
                }
            }
            is AppResult.Failure -> {
                println("\n${ANSI.RED}[오류] 목록을 불러오지 못했습니다: ${result.message}${ANSI.RESET}")
            }
        }
        pause()
    }

    private fun showStatistics() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.GREEN}┌── 라이브러리 요약 통계 ──────────────────────┐${ANSI.RESET}")
        when (val result = client.getStats()) {
            is AppResult.Success -> {
                println(result.value)
            }
            is AppResult.Failure -> {
                println("\n${ANSI.RED}[오류] 통계를 가져오지 못했습니다: ${result.message}${ANSI.RESET}")
            }
        }
        pause()
    }

    private fun printItemsTable(items: List<MediaItem>, title: String) {
        println("\n${ANSI.BOLD}[$title]${ANSI.RESET} (총 ${items.size}개)")
        if (items.isEmpty()) {
            println("  표시할 항목이 없습니다.")
            return
        }
        println("--------------------------------------------------------------------------------")
        items.forEach { item ->
            val favEmoji = if (item.isFavorite) "[*]" else "[ ]"
            val tagsStr = if (item.tags.isNotEmpty() && item.tags.first().isNotBlank()) {
                " [태그: " + item.tags.joinToString(", ") + "]"
            } else ""
            val memoStr = if (item.memo.isNotBlank()) " | 메모: ${item.memo}" else ""
            println("  $favEmoji ${item.toDisplayString()}$memoStr$tagsStr")
        }
        println("--------------------------------------------------------------------------------")
    }

    private fun clearScreen() {
        print("\u001b[H\u001b[2J")
        System.out.flush()
    }

    private fun pause() {
        print("\n계속하려면 Enter 키를 누르세요...")
        readLine()
    }

    object ANSI {
        const val RESET = "\u001B[0m"
        const val BOLD = "\u001B[1m"
        const val RED = "\u001B[31m"
        const val GREEN = "\u001B[32m"
        const val YELLOW = "\u001B[33m"
        const val BLUE = "\u001B[34m"
        const val PURPLE = "\u001B[35m"
        const val CYAN = "\u001B[36m"
    }
}