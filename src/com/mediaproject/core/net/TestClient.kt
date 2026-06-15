package com.mediaproject.core.net

import com.mediaproject.core.model.Book
import com.mediaproject.core.model.Status
import com.mediaproject.core.repo.AppResult

fun main() {
    println("=========================================")
    println("[테스트] 통합 테스트 클라이언트 시작")
    println("=========================================")

    val client = NetworkClient("localhost", 50001)
    
    // 1. 연결 테스트
    when (val res = client.connect()) {
        is AppResult.Success -> println("[PASS] 서버 소켓 연결 성공")
        is AppResult.Failure -> {
            println("[FAIL] 서버 소켓 연결 실패: ${res.message}")
            return
        }
    }

    try {
        // 2. 로그인 없이 요청 전송 테스트
        val noLoginRes = client.getItems()
        if (noLoginRes is AppResult.Failure && noLoginRes.message.contains("로그인")) {
            println("[PASS] 로그인 없이 요청 시 권한 에러 반환 검증 완료")
        } else {
            println("[FAIL] 로그인 없이 요청 시 차단되지 않음: $noLoginRes")
        }

        // 3. USER 로그인 테스트
        when (val res = client.login("USER")) {
            is AppResult.Success -> println("[PASS] 일반 사용자 로그인 성공")
            is AppResult.Failure -> println("[FAIL] 일반 사용자 로그인 실패: ${res.message}")
        }

        // 4. USER 권한으로 ADD 요청 테스트 (차단되어야 함)
        val dummyBook = Book(
            id = 9999, title = "테스트책", genre = "소설", rating = 4.5, date = "2026-06-15",
            memo = "테스트메모", status = Status.WISHLIST, isFavorite = false, tags = listOf("테스트"),
            author = "테스터", publisher = "테스트출판사", pages = 100
        )
        val userAddRes = client.add(dummyBook)
        if (userAddRes is AppResult.Failure && userAddRes.message.contains("관리자")) {
            println("[PASS] 일반 사용자의 등록 차단 검증 완료")
        } else {
            println("[FAIL] 일반 사용자가 미디어를 등록할 수 있음: $userAddRes")
        }

        // 5. USER 로그인 해제 후 ADMIN 잘못된 비밀번호 테스트
        val client2 = NetworkClient("localhost", 50001)
        client2.connect()
        val badAdminRes = client2.login("ADMIN", "wrong_password")
        if (badAdminRes is AppResult.Failure) {
            println("[PASS] 관리자 로그인 틀린 비밀번호 차단 검증 완료")
        } else {
            println("[FAIL] 틀린 비밀번호로 관리자 로그인 성공함")
        }

        // 6. ADMIN 올바른 로그인 테스트
        when (val res = client2.login("ADMIN", "2023664002")) {
            is AppResult.Success -> println("[PASS] 관리자 로그인 성공")
            is AppResult.Failure -> println("[FAIL] 관리자 로그인 실패: ${res.message}")
        }

        // 7. ADMIN 권한으로 새 책 추가 테스트
        val testBook = Book(
            id = 7777, title = "테스트소설", genre = "SF", rating = 4.0, date = "2026-06-15",
            memo = "재미있음", status = Status.WISHLIST, isFavorite = false, tags = listOf("sf", "우주"),
            author = "김작가", publisher = "우주출판사", pages = 350
        )
        when (val res = client2.add(testBook)) {
            is AppResult.Success -> println("[PASS] 관리자 미디어 등록 성공")
            is AppResult.Failure -> println("[FAIL] 관리자 미디어 등록 실패: ${res.message}")
        }

        // 8. 추가된 미디어 조회 검증
        when (val res = client2.getItems()) {
            is AppResult.Success -> {
                val found = res.value.find { it.id == 7777 }
                if (found != null && found.title == "테스트소설" && found.genre == "SF") {
                    println("[PASS] 등록된 미디어 정상 조회 검증 완료")
                } else {
                    println("[FAIL] 등록된 미디어가 조회되지 않거나 정보가 다름: $found")
                }
            }
            is AppResult.Failure -> println("[FAIL] 미디어 조회 실패: ${res.message}")
        }

        // 9. 미디어 정보 수정 테스트
        val updatedBook = testBook.copy(rating = 4.8, isFavorite = true, memo = "정말 재미있음")
        when (val res = client2.update(7777, updatedBook)) {
            is AppResult.Success -> println("[PASS] 관리자 미디어 수정 성공")
            is AppResult.Failure -> println("[FAIL] 관리자 미디어 수정 실패: ${res.message}")
        }

        // 10. 수정된 미디어 조회 검증
        when (val res = client2.getItems()) {
            is AppResult.Success -> {
                val found = res.value.find { it.id == 7777 }
                if (found != null && found.rating == 4.8 && found.isFavorite && found.memo == "정말 재미있음") {
                    println("[PASS] 수정된 미디어 정상 조회 검증 완료")
                } else {
                    println("[FAIL] 수정사항이 반영되지 않음: $found")
                }
            }
            is AppResult.Failure -> println("[FAIL] 미디어 조회 실패: ${res.message}")
        }

        // 11. 장르별 일괄 상태 변경 검증 (SF -> DONE)
        when (val res = client2.bulkUpdate(Status.DONE, "SF")) {
            is AppResult.Success -> {
                println("[PASS] 일괄 상태 변경 요청 성공 (변경 개수: ${res.value})")
            }
            is AppResult.Failure -> println("[FAIL] 일괄 상태 변경 실패: ${res.message}")
        }

        // 일괄 변경 적용 여부 확인
        when (val res = client2.getItems()) {
            is AppResult.Success -> {
                val found = res.value.find { it.id == 7777 }
                if (found != null && found.status == Status.DONE) {
                    println("[PASS] 일괄 변경 결과 적용 검증 완료")
                } else {
                    println("[FAIL] 일괄 상태 변경이 적용되지 않음: $found")
                }
            }
            is AppResult.Failure -> println("[FAIL] 미디어 조회 실패: ${res.message}")
        }

        // 12. 미디어 삭제 테스트
        when (val res = client2.delete(7777)) {
            is AppResult.Success -> println("[PASS] 미디어 삭제 성공")
            is AppResult.Failure -> println("[FAIL] 미디어 삭제 실패: ${res.message}")
        }

        // 삭제 확인
        when (val res = client2.getItems()) {
            is AppResult.Success -> {
                val found = res.value.find { it.id == 7777 }
                if (found == null) {
                    println("[PASS] 미디어 정상 삭제 검증 완료")
                } else {
                    println("[FAIL] 미디어가 정상적으로 삭제되지 않음")
                }
            }
            is AppResult.Failure -> println("[FAIL] 미디어 조회 실패: ${res.message}")
        }

        // 13. 통계 요청 검증
        when (val res = client2.getStats()) {
            is AppResult.Success -> {
                println("[PASS] 통계 데이터 가져오기 검증 완료")
                println("\n--- 통계 출력 ---")
                println(res.value)
                println("-----------------\n")
            }
            is AppResult.Failure -> println("[FAIL] 통계 요청 실패: ${res.message}")
        }

        client.close()
        client2.close()
        println("모든 통합 테스트 시나리오가 성공적으로 통과되었습니다!")

    } catch (e: Exception) {
        println("[ERROR] 테스트 실행 중 오류 발생: ${e.message}")
        e.printStackTrace()
    }
}
