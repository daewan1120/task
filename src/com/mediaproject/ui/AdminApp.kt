package com.mediaproject.ui

import com.mediaproject.core.net.NetworkClient
import com.mediaproject.core.model.*
import com.mediaproject.core.repo.AppResult

class AdminApp(private val client: NetworkClient) {
    private val userMode = UserApp(client)

    fun run() {
        var running = true
        while (running) {
            clearScreen()
            println("${ANSI.BOLD}${ANSI.PURPLE}┌──────────────────────────────────────────────┐${ANSI.RESET}")
            println("${ANSI.BOLD}${ANSI.PURPLE}│              관리자 제어 센터               │${ANSI.RESET}")
            println("${ANSI.BOLD}${ANSI.PURPLE}└──────────────────────────────────────────────┘${ANSI.RESET}")
            println(" 1) 사용자 기능 실행 (조회, 검색, 정렬 등)")
            println(" 2) 새 미디어 등록")
            println(" 3) 미디어 삭제")
            println(" 4) 장르별 일괄 상태 변경")
            println(" 5) 특정 미디어 세부 정보 수정")
            println(" 0) 관리자 모드 종료")
            print("\n>> 선택 > ")

            when (readLine()?.trim()) {
                "1" -> userMode.run()
                "2" -> menuAdd()
                "3" -> menuDelete()
                "4" -> menuBulkUpdate()
                "5" -> menuEditItem()
                "0" -> running = false
                else -> {
                    println("\n${ANSI.RED}[오류] 잘못된 선택입니다. 다시 입력해 주세요.${ANSI.RESET}")
                    pause()
                }
            }
        }
    }

    private fun menuAdd() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.YELLOW}┌── 새 미디어 등록 ────────────────────────────┐${ANSI.RESET}")
        println(" 등록할 미디어 종류를 선택하세요:")
        println(" 1) 책 (Book)")
        println(" 2) 영화 (Movie)")
        println(" 3) 음악 (Music)")
        println(" 0) 취소")
        print("\n>> 선택 > ")

        val choice = readLine()?.trim()
        if (choice == "0" || choice.isNullOrBlank()) return

        val itemsResult = client.getItems()
        if (itemsResult is AppResult.Failure) {
            println("\n${ANSI.RED}[오류] 서버에서 목록을 불러오지 못해 새 ID를 생성할 수 없습니다: ${itemsResult.message}${ANSI.RESET}")
            pause()
            return
        }
        val items = (itemsResult as AppResult.Success).value
        val nextId = (items.maxOfOrNull { it.id } ?: 0) + 1

        val builder = when (choice) {
            "1" -> buildBook(nextId)
            "2" -> buildMovie(nextId)
            "3" -> buildMusic(nextId)
            else -> {
                println("\n${ANSI.RED}[오류] 잘못된 선택입니다.${ANSI.RESET}")
                pause()
                return
            }
        }

        if (builder == null) {
            println("\n${ANSI.RED}[안내] 등록이 취소되었습니다.${ANSI.RESET}")
            pause()
            return
        }

        when (val result = client.add(builder)) {
            is AppResult.Success -> println("\n${ANSI.GREEN}[완료] 미디어가 성공적으로 등록되었습니다! (ID: $nextId)${ANSI.RESET}")
            is AppResult.Failure -> println("\n${ANSI.RED}[오류] 등록 실패: ${result.message}${ANSI.RESET}")
        }
        pause()
    }

    private fun menuDelete() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.YELLOW}┌── 미디어 삭제 ───────────────────────────────┐${ANSI.RESET}")
        
        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val items = result.value
                if (items.isEmpty()) {
                    println("삭제할 미디어가 없습니다.")
                    pause()
                    return
                }
                printMiniList(items)
                print("\n삭제할 미디어 ID 입력 > ")
                val id = readLine()?.trim()?.toIntOrNull()
                if (id == null) {
                    println("\n${ANSI.RED}[오류] 올바른 ID 숫자를 입력해 주세요.${ANSI.RESET}")
                    pause()
                    return
                }

                val target = items.find { it.id == id }
                if (target == null) {
                    println("\n${ANSI.RED}[오류] 해당 ID의 항목이 없습니다.${ANSI.RESET}")
                    pause()
                    return
                }

                print("정말로 '${target.title}'을(를) 삭제하시겠습니까? (y/N) > ")
                val confirm = readLine()?.trim()?.lowercase()
                if (confirm == "y" || confirm == "yes") {
                    when (val delResult = client.delete(id)) {
                        is AppResult.Success -> println("\n${ANSI.GREEN}[완료] 성공적으로 삭제되었습니다.${ANSI.RESET}")
                        is AppResult.Failure -> println("\n${ANSI.RED}[오류] 삭제 실패: ${delResult.message}${ANSI.RESET}")
                    }
                } else {
                    println("\n삭제가 취소되었습니다.")
                }
            }
            is AppResult.Failure -> println("\n${ANSI.RED}[오류] 서버 오류: ${result.message}${ANSI.RESET}")
        }
        pause()
    }

    private fun menuBulkUpdate() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.YELLOW}┌── 장르별 일괄 상태 변경 ─────────────────────┐${ANSI.RESET}")
        print("상태를 변경할 장르명을 입력하세요 > ")
        val genre = readLine()?.trim() ?: ""
        if (genre.isBlank() || genre.contains("|")) {
            println("\n${ANSI.RED}[오류] 올바르지 않은 장르명입니다.${ANSI.RESET}")
            pause()
            return
        }

        println("\n변경할 대상 상태를 선택하세요:")
        println(" 1) 보고싶다 (WISHLIST)")
        println(" 2) 진행중 (IN_PROGRESS)")
        println(" 3) 완료 (DONE)")
        print("\n>> 선택 > ")
        val statusChoice = readLine()?.trim()
        val newStatus = when (statusChoice) {
            "1" -> Status.WISHLIST
            "2" -> Status.IN_PROGRESS
            "3" -> Status.DONE
            else -> {
                println("\n${ANSI.RED}[오류] 잘못된 선택입니다.${ANSI.RESET}")
                pause()
                return
            }
        }

        print("정말로 장르 '$genre'인 항목들의 상태를 $newStatus(으)로 일괄 변경하시겠습니까? (y/N) > ")
        if (readLine()?.trim()?.lowercase() != "y") {
            println("\n취소되었습니다.")
            pause()
            return
        }

        when (val result = client.bulkUpdate(newStatus, genre)) {
            is AppResult.Success -> println("\n${ANSI.GREEN}[완료] 완료! 총 ${result.value}개의 항목이 $newStatus 상태로 변경되었습니다.${ANSI.RESET}")
            is AppResult.Failure -> println("\n${ANSI.RED}[오류] 변경 실패: ${result.message}${ANSI.RESET}")
        }
        pause()
    }

    private fun menuEditItem() {
        clearScreen()
        println("${ANSI.BOLD}${ANSI.YELLOW}┌── 미디어 세부 정보 수정 ─────────────────────┐${ANSI.RESET}")

        when (val result = client.getItems()) {
            is AppResult.Success -> {
                val items = result.value
                if (items.isEmpty()) {
                    println("수정할 미디어가 없습니다.")
                    pause()
                    return
                }
                printMiniList(items)
                print("\n수정할 미디어 ID 입력 > ")
                val id = readLine()?.trim()?.toIntOrNull()
                if (id == null) {
                    println("\n${ANSI.RED}[오류] 올바른 ID 숫자를 입력해 주세요.${ANSI.RESET}")
                    pause()
                    return
                }

                val item = items.find { it.id == id }
                if (item == null) {
                    println("\n${ANSI.RED}[오류] 해당 ID의 항목이 없습니다.${ANSI.RESET}")
                    pause()
                    return
                }

                println("\n[수정 대상을 지정하세요. 빈 칸으로 입력 시 기존 값 유지]")
                val newTitle = inputRequiredWithDefault("제목", item.title)
                val newGenre = inputRequiredWithDefault("장르", item.genre)
                val newRating = inputRatingWithDefault("평점 (0.0~5.0)", item.rating)
                val newDate = inputDateWithDefault("날짜 (yyyy-MM-dd)", item.date)
                val newMemo = inputMemoWithDefault("메모", item.memo)
                val newTags = inputTagsWithDefault("태그 (쉼표로 구분)", item.tags)

                // 미디어 타입에 맞는 상세 필드 수정
                val updatedItem = when (item) {
                    is Book -> {
                        val author = inputRequiredWithDefault("저자", item.author)
                        val publisher = inputWithDefault("출판사", item.publisher)
                        val pages = inputPositiveIntWithDefault("쪽수", item.pages)
                        item.copy(
                            title = newTitle, genre = newGenre, rating = newRating, date = newDate,
                            memo = newMemo, tags = newTags, author = author, publisher = publisher, pages = pages
                        )
                    }
                    is Movie -> {
                        val director = inputRequiredWithDefault("감독", item.director)
                        val runningTime = inputPositiveIntWithDefault("상영 시간(분)", item.runningTime)
                        val ageRating = inputWithDefault("관람 등급", item.ageRating)
                        item.copy(
                            title = newTitle, genre = newGenre, rating = newRating, date = newDate,
                            memo = newMemo, tags = newTags, director = director, runningTime = runningTime, ageRating = ageRating
                        )
                    }
                    is Music -> {
                        val artist = inputRequiredWithDefault("아티스트", item.artist)
                        val album = inputWithDefault("앨범", item.album)
                        val trackCount = inputPositiveIntWithDefault("트랙 수", item.trackCount)
                        item.copy(
                            title = newTitle, genre = newGenre, rating = newRating, date = newDate,
                            memo = newMemo, tags = newTags, artist = artist, album = album, trackCount = trackCount
                        )
                    }
                }

                when (val updateResult = client.update(id, updatedItem)) {
                    is AppResult.Success -> println("\n${ANSI.GREEN}[완료] 세부 정보가 정상적으로 수정되었습니다.${ANSI.RESET}")
                    is AppResult.Failure -> println("\n${ANSI.RED}[오류] 수정 실패: ${updateResult.message}${ANSI.RESET}")
                }
            }
            is AppResult.Failure -> println("\n${ANSI.RED}[오류] 서버 오류: ${result.message}${ANSI.RESET}")
        }
        pause()
    }

    private fun buildBook(id: Int): Book? {
        println("\n[책] 정보 입력 (도중에 취소하려면 '제목'에 빈칸 입력)")
        val title = inputRequiredOrCancel("제목") ?: return null
        val author = inputRequired("저자")
        val genre = inputRequired("장르")
        val rating = inputRating()
        val date = inputDate()
        val publisher = input("출판사")
        val pages = inputPositiveInt("쪽수")

        return Book(
            id = id, title = title, genre = genre, rating = rating, date = date,
            memo = "", status = Status.WISHLIST, isFavorite = false, tags = emptyList(),
            author = author, publisher = publisher, pages = pages
        )
    }

    private fun buildMovie(id: Int): Movie? {
        println("\n[영화] 정보 입력 (도중에 취소하려면 '제목'에 빈칸 입력)")
        val title = inputRequiredOrCancel("제목") ?: return null
        val director = inputRequired("감독")
        val genre = inputRequired("장르")
        val rating = inputRating()
        val date = inputDate()
        val runningTime = inputPositiveInt("상영 시간(분)")
        val ageRating = input("관람 등급")

        return Movie(
            id = id, title = title, genre = genre, rating = rating, date = date,
            memo = "", status = Status.WISHLIST, isFavorite = false, tags = emptyList(),
            director = director, runningTime = runningTime, ageRating = ageRating
        )
    }

    private fun buildMusic(id: Int): Music? {
        println("\n[음악] 정보 입력 (도중에 취소하려면 '제목'에 빈칸 입력)")
        val title = inputRequiredOrCancel("제목") ?: return null
        val artist = inputRequired("아티스트")
        val genre = inputRequired("장르")
        val rating = inputRating()
        val date = inputDate()
        val album = input("앨범")
        val trackCount = inputPositiveInt("트랙 수")

        return Music(
            id = id, title = title, genre = genre, rating = rating, date = date,
            memo = "", status = Status.WISHLIST, isFavorite = false, tags = emptyList(),
            artist = artist, album = album, trackCount = trackCount
        )
    }

    // --- 입력 유틸리티 (구분자 '|' 검증 기능 포함) ---

    private fun input(label: String): String {
        while (true) {
            print("  $label > ")
            val input = readLine()?.trim() ?: ""
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 구분자 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input
        }
    }

    private fun inputRequired(label: String): String {
        var input: String
        do {
            print("  $label (필수) > ")
            input = readLine()?.trim() ?: ""
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 구분자 문자는 포함될 수 없습니다.${ANSI.RESET}")
                input = ""
            }
        } while (input.isBlank())
        return input
    }

    private fun inputRequiredOrCancel(label: String): String? {
        print("  $label (취소하려면 엔터) > ")
        val input = readLine()?.trim() ?: ""
        if (input.isBlank()) return null
        if (input.contains("|")) {
            println("  ${ANSI.RED}[오류] '|' 구분자 문자는 포함될 수 없습니다.${ANSI.RESET}")
            return inputRequiredOrCancel(label)
        }
        return input
    }

    private fun inputRating(): Double {
        var r: Double?
        do {
            print("  평점 (0.0~5.0) > ")
            r = readLine()?.trim()?.toDoubleOrNull()
        } while (r == null || r !in 0.0..5.0)
        return r
    }

    private fun inputDate(): String {
        while (true) {
            print("  날짜 (yyyy-MM-dd, 기본값 오늘) > ")
            val inDate = readLine()?.trim() ?: ""
            if (inDate.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return if (inDate.isBlank()) java.time.LocalDate.now().toString() else inDate
        }
    }

    private fun inputPositiveInt(label: String): Int {
        var n: Int?
        do {
            print("  $label (양의 정수) > ")
            n = readLine()?.trim()?.toIntOrNull()
        } while (n == null || n <= 0)
        return n
    }

    // --- 기본값 제공 입력 유틸리티 (수정용) ---

    private fun inputWithDefault(label: String, default: String): String {
        while (true) {
            print("  $label [$default] > ")
            val input = readLine()?.trim() ?: ""
            if (input.isEmpty()) return default
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 구분자 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input
        }
    }

    private fun inputRequiredWithDefault(label: String, default: String): String {
        while (true) {
            print("  $label (필수) [$default] > ")
            val input = readLine()?.trim() ?: ""
            if (input.isEmpty()) return default
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 구분자 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input
        }
    }

    private fun inputRatingWithDefault(label: String, default: Double): Double {
        var r: Double?
        do {
            print("  $label [$default] > ")
            val line = readLine()?.trim() ?: ""
            if (line.isEmpty()) return default
            r = line.toDoubleOrNull()
        } while (r == null || r !in 0.0..5.0)
        return r
    }

    private fun inputDateWithDefault(label: String, default: String): String {
        while (true) {
            print("  $label [$default] > ")
            val input = readLine()?.trim() ?: ""
            if (input.isEmpty()) return default
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input
        }
    }

    private fun inputMemoWithDefault(label: String, default: String): String {
        while (true) {
            print("  $label [$default] > ")
            val input = readLine()?.trim() ?: ""
            if (input.isEmpty()) return default
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input
        }
    }

    private fun inputTagsWithDefault(label: String, default: List<String>): List<String> {
        val defaultStr = default.joinToString(",")
        while (true) {
            print("  $label [$defaultStr] > ")
            val input = readLine()?.trim() ?: ""
            if (input.isEmpty()) return default
            if (input.contains("|")) {
                println("  ${ANSI.RED}[오류] '|' 문자는 포함될 수 없습니다.${ANSI.RESET}")
                continue
            }
            return input.split(",").map { it.trim() }
        }
    }

    private fun inputPositiveIntWithDefault(label: String, default: Int): Int {
        var n: Int?
        do {
            print("  $label [$default] > ")
            val line = readLine()?.trim() ?: ""
            if (line.isEmpty()) return default
            n = line.toIntOrNull()
        } while (n == null || n <= 0)
        return n
    }

    // --- 화면 및 헬퍼 ---

    private fun printMiniList(items: List<MediaItem>) {
        println("--------------------------------------------------------------------------------")
        items.forEach { item ->
            println("  [ID: ${item.id}] ${item.toDisplayString()}")
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
        const val PURPLE = "\u001B[35m"
    }
}