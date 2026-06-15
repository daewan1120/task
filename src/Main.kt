import com.mediaproject.core.net.NetworkClient
import com.mediaproject.core.repo.AppResult
import com.mediaproject.ui.AdminApp
import com.mediaproject.ui.UserApp

fun main() {
    println("=========================================")
    println("개인 미디어 관리 CLI 클라이언트")
    println("=========================================")
    print("서버 주소 입력 (기본값: localhost) > ")
    val hostInput = readLine()?.trim() ?: ""
    val host = if (hostInput.isEmpty()) "localhost" else hostInput

    print("서버 포트 입력 (기본값: 50001) > ")
    val portInput = readLine()?.trim() ?: ""
    val port = if (portInput.isEmpty()) 50001 else portInput.toIntOrNull() ?: 50001

    println("\n$host:$port 서버에 연결을 시도하는 중...")
    val client = NetworkClient(host, port)
    when (val connRes = client.connect()) {
        is AppResult.Failure -> {
            println("[오류] 서버 연결 실패: ${connRes.message}")
            return
        }
        is AppResult.Success -> {
            println("[연결] 서버에 성공적으로 연결되었습니다.")
        }
    }

    try {
        var running = true
        while (running) {
            println("\n=========================================")
            println("실행 모드를 선택해 주세요:")
            println(" 1) 일반 사용자 모드 (조회, 검색, 통계 등)")
            println(" 2) 관리자 모드 (등록, 수정, 삭제, 일괄변경)")
            println(" 0) 종료")
            print("\n👉 선택 > ")

            when (readLine()?.trim()) {
                "1" -> {
                    when (val loginRes = client.login("USER")) {
                        is AppResult.Success -> {
                            UserApp(client).run()
                        }
                        is AppResult.Failure -> {
                            println("[오류] 로그인 실패: ${loginRes.message}")
                        }
                    }
                }
                "2" -> {
                    print("비밀번호 입력 > ")
                    val password = readLine() ?: ""
                    when (val loginRes = client.login("ADMIN", password)) {
                        is AppResult.Success -> {
                            AdminApp(client).run()
                        }
                        is AppResult.Failure -> {
                            println("[오류] 관리자 인증 실패: ${loginRes.message}")
                        }
                    }
                }
                "0" -> {
                    running = false
                }
                else -> {
                    println("[오류] 잘못된 선택입니다.")
                }
            }
        }
    } finally {
        client.close()
        println("\n클라이언트를 종료합니다. 이용해 주셔서 감사합니다.")
    }
}