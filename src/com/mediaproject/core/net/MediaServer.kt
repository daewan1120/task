package com.mediaproject.core.net

import com.mediaproject.core.library.MediaLibrary
import com.mediaproject.core.model.Status
import com.mediaproject.core.repo.MediaRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class MediaServer(private val port: Int = 50001) {
    private val repo = MediaRepository()
    private val library = MediaLibrary(repo)

    fun start() {
        val serverSocket = ServerSocket(port)
        println("=========================================")
        println("[서버] 미디어 라이브러리 서버가 시작되었습니다.")
        println("[서버] 포트: $port 에서 연결 대기 중...")
        println("=========================================")

        try {
            while (true) {
                val clientSocket = serverSocket.accept()
                println("[연결] 새 클라이언트 연결됨: ${clientSocket.remoteSocketAddress}")
                thread { handleClient(clientSocket) }
            }
        } catch (e: Exception) {
            println("[오류] 서버 소켓 오류: ${e.message}")
        } finally {
            serverSocket.close()
        }
    }

    private fun handleClient(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        val writer = PrintWriter(socket.getOutputStream().bufferedWriter(Charsets.UTF_8), true)

        var role: String? = null // USER 또는 ADMIN

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val commandLine = line!!.trim()
                if (commandLine.isEmpty()) continue

                val parts = commandLine.split(" ")
                val cmd = parts[0].uppercase()

                // 1. 로그인 처리
                if (cmd == "LOGIN") {
                    if (parts.size < 2) {
                        writer.println("ERROR:로그인 인자가 부족합니다.")
                        continue
                    }
                    val reqRole = parts[1].uppercase()
                    if (reqRole == "USER") {
                        role = "USER"
                        writer.println("SUCCESS")
                        println("[로그인] [${socket.remoteSocketAddress}] 일반 사용자 로그인 성공")
                    } else if (reqRole == "ADMIN") {
                        if (parts.size < 3) {
                            writer.println("ERROR:비밀번호를 입력하세요.")
                            continue
                        }
                        val pwd = parts[2]
                        if (pwd == "2023664002") {
                            role = "ADMIN"
                            writer.println("SUCCESS")
                            println("[로그인] [${socket.remoteSocketAddress}] 관리자 로그인 성공")
                        } else {
                            writer.println("ERROR:비밀번호가 틀렸습니다.")
                        }
                    } else {
                        writer.println("ERROR:알 수 없는 역할군입니다.")
                    }
                    continue
                }

                // 로그인 여부 확인
                if (role == null) {
                    writer.println("ERROR:로그인이 필요합니다.")
                    continue
                }

                // 2. 명령어 처리 분기
                when (cmd) {
                    "GET_ITEMS" -> {
                        val items = synchronized(library) { library.items }
                        for (item in items) {
                            writer.println(item.toCsv())
                        }
                        writer.println("__END_OF_RESPONSE__")
                    }
                    "STATS" -> {
                        val stats = synchronized(library) { library.statistics() }
                        writer.println(stats)
                        writer.println("__END_OF_RESPONSE__")
                    }
                    "ADD" -> {
                        if (role != "ADMIN") {
                            writer.println("ERROR:관리자 권한이 필요합니다.")
                            continue
                        }
                        val csvData = commandLine.substringAfter("ADD ").trim()
                        val item = repo.parseLine(csvData)
                        if (item != null) {
                            synchronized(library) { library.add(item) }
                            writer.println("SUCCESS")
                            println("[추가] 데이터 추가 완료: ${item.title} (ID: ${item.id})")
                        } else {
                            writer.println("ERROR:데이터 파싱에 실패했습니다.")
                        }
                    }
                    "DELETE" -> {
                        if (role != "ADMIN") {
                            writer.println("ERROR:관리자 권한이 필요합니다.")
                            continue
                        }
                        val id = parts.getOrNull(1)?.toIntOrNull()
                        if (id == null) {
                            writer.println("ERROR:ID가 올바르지 않습니다.")
                            continue
                        }
                        synchronized(library) {
                            if (library.findById(id) != null) {
                                library.remove(id)
                                writer.println("SUCCESS")
                                println("[삭제] 데이터 삭제 완료 (ID: $id)")
                            } else {
                                writer.println("ERROR:해당 ID를 가진 미디어가 없습니다.")
                            }
                        }
                    }
                    "UPDATE" -> {
                        // 유저도 즐겨찾기는 토글할 수 있으므로, 권한은 유저/관리자 모두 가능
                        val cmdParts = commandLine.split(" ", limit = 3)
                        if (cmdParts.size < 3) {
                            writer.println("ERROR:명령어 인자가 올바르지 않습니다.")
                            continue
                        }
                        val id = cmdParts[1].toIntOrNull()
                        val csvData = cmdParts[2]
                        val newItem = repo.parseLine(csvData)

                        if (id == null || newItem == null) {
                            writer.println("ERROR:수정 실패 (데이터 파싱 오류)")
                            continue
                        }

                        synchronized(library) {
                            val exist = library.findById(id)
                            if (exist == null) {
                                writer.println("ERROR:해당 ID를 찾을 수 없습니다.")
                            } else {
                                // 일반 사용자는 즐겨찾기 변경만 허용하고 다른 데이터 수정은 불가하도록 제한
                                if (role == "USER") {
                                    // 기존 정보에서 isFavorite만 변경되었는지 확인
                                    val originalWithToggledFavorite = exist.withFavorite(newItem.isFavorite)
                                    // CSV 변환 후 비교하여 즐겨찾기 외의 다른 값이 변했는지 체크
                                    if (originalWithToggledFavorite.toCsv() != newItem.toCsv()) {
                                        writer.println("ERROR:일반 사용자는 즐겨찾기 상태만 수정할 수 있습니다.")
                                        return@synchronized
                                    }
                                }
                                library.update(id) { newItem }
                                writer.println("SUCCESS")
                                println("[수정] 데이터 수정 완료 (ID: $id)")
                            }
                        }
                    }
                    "BULK_UPDATE" -> {
                        if (role != "ADMIN") {
                            writer.println("ERROR:관리자 권한이 필요합니다.")
                            continue
                        }
                        val cmdParts = commandLine.split(" ", limit = 3)
                        if (cmdParts.size < 3) {
                            writer.println("ERROR:명령어 형식이 잘못되었습니다. (BULK_UPDATE <STATUS> <GENRE>)")
                            continue
                        }
                        val statusStr = cmdParts[1]
                        val genre = cmdParts[2]
                        val status = try { Status.valueOf(statusStr.uppercase()) } catch (e: Exception) { null }

                        if (status == null) {
                            writer.println("ERROR:올바르지 않은 상태 형식입니다. (WISHLIST, IN_PROGRESS, DONE)")
                            continue
                        }

                        var count = 0
                        synchronized(library) {
                            library.bulkUpdate({ it.genre.equals(genre, ignoreCase = true) }) {
                                count++
                                it.withStatus(status)
                            }
                        }
                        writer.println("SUCCESS $count")
                        println("[일괄수정] 일괄 상태 변경 완료 (장르: $genre, 상태: $status, 변경 개수: $count)")
                    }
                    else -> {
                        writer.println("ERROR:알 수 없는 명령어입니다.")
                    }
                }
            }
        } catch (e: Exception) {
            println("[연결] [${socket.remoteSocketAddress}] 연결 예외 발생: ${e.message}")
        } finally {
            socket.close()
            println("[연결] [${socket.remoteSocketAddress}] 클라이언트 연결 끊김")
        }
    }
}

fun main() {
    val server = MediaServer()
    server.start()
}
