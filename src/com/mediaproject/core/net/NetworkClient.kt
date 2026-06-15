package com.mediaproject.core.net

import com.mediaproject.core.model.MediaItem
import com.mediaproject.core.model.Status
import com.mediaproject.core.repo.AppResult
import com.mediaproject.core.repo.MediaRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class NetworkClient(private val host: String = "localhost", private val port: Int = 50001) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private val repo = MediaRepository("") // CSV 파일 입출력은 안 하지만 파싱용으로 사용

    fun connect(): AppResult<Unit> {
        return try {
            val sock = Socket(host, port)
            socket = sock
            reader = BufferedReader(InputStreamReader(sock.getInputStream(), "UTF-8"))
            writer = PrintWriter(sock.getOutputStream().bufferedWriter(Charsets.UTF_8), true)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure("서버 연결 실패: ${e.message}")
        }
    }

    fun login(role: String, password: String? = null): AppResult<Unit> {
        val w = writer ?: return AppResult.Failure("서버가 연결되어 있지 않습니다.")
        val r = reader ?: return AppResult.Failure("서버가 연결되어 있지 않습니다.")

        val loginCmd = if (role.uppercase() == "ADMIN") {
            "LOGIN ADMIN ${password ?: ""}"
        } else {
            "LOGIN USER"
        }

        return try {
            w.println(loginCmd)
            val resp = r.readLine() ?: ""
            if (resp == "SUCCESS") {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(resp.substringAfter("ERROR:"))
            }
        } catch (e: Exception) {
            AppResult.Failure("로그인 요청 실패: ${e.message}")
        }
    }

    fun getItems(): AppResult<List<MediaItem>> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("GET_ITEMS")
            val firstLine = r.readLine() ?: return AppResult.Failure("서버 응답 없음")
            if (firstLine.startsWith("ERROR:")) {
                return AppResult.Failure(firstLine.substringAfter("ERROR:"))
            }
            
            val items = mutableListOf<MediaItem>()
            if (firstLine != "__END_OF_RESPONSE__") {
                val parsed = repo.parseLine(firstLine)
                if (parsed != null) {
                    items.add(parsed)
                }
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    val lineStr = line!!
                    if (lineStr == "__END_OF_RESPONSE__") break
                    val nextParsed = repo.parseLine(lineStr)
                    if (nextParsed != null) {
                        items.add(nextParsed)
                    }
                }
            }
            AppResult.Success(items)
        } catch (e: Exception) {
            AppResult.Failure("목록 조회 중 오류: ${e.message}")
        }
    }

    fun getStats(): AppResult<String> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("STATS")
            val firstLine = r.readLine() ?: return AppResult.Failure("서버 응답 없음")
            if (firstLine.startsWith("ERROR:")) {
                return AppResult.Failure(firstLine.substringAfter("ERROR:"))
            }

            val sb = StringBuilder()
            if (firstLine != "__END_OF_RESPONSE__") {
                sb.append(firstLine).append("\n")
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    val lineStr = line!!
                    if (lineStr == "__END_OF_RESPONSE__") break
                    sb.append(lineStr).append("\n")
                }
            }
            AppResult.Success(sb.toString().trimEnd())
        } catch (e: Exception) {
            AppResult.Failure("통계 조회 중 오류: ${e.message}")
        }
    }

    fun add(item: MediaItem): AppResult<Unit> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("ADD ${item.toCsv()}")
            val resp = r.readLine() ?: ""
            if (resp == "SUCCESS") {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(resp.substringAfter("ERROR:"))
            }
        } catch (e: Exception) {
            AppResult.Failure("추가 요청 실패: ${e.message}")
        }
    }

    fun delete(id: Int): AppResult<Unit> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("DELETE $id")
            val resp = r.readLine() ?: ""
            if (resp == "SUCCESS") {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(resp.substringAfter("ERROR:"))
            }
        } catch (e: Exception) {
            AppResult.Failure("삭제 요청 실패: ${e.message}")
        }
    }

    fun update(id: Int, item: MediaItem): AppResult<Unit> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("UPDATE $id ${item.toCsv()}")
            val resp = r.readLine() ?: ""
            if (resp == "SUCCESS") {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(resp.substringAfter("ERROR:"))
            }
        } catch (e: Exception) {
            AppResult.Failure("수정 요청 실패: ${e.message}")
        }
    }

    fun bulkUpdate(status: Status, genre: String): AppResult<Int> {
        val w = writer ?: return AppResult.Failure("서버 연결 없음")
        val r = reader ?: return AppResult.Failure("서버 연결 없음")

        return try {
            w.println("BULK_UPDATE ${status.name} $genre")
            val resp = r.readLine() ?: ""
            if (resp.startsWith("SUCCESS")) {
                val count = resp.substringAfter("SUCCESS ").trim().toIntOrNull() ?: 0
                AppResult.Success(count)
            } else {
                AppResult.Failure(resp.substringAfter("ERROR:"))
            }
        } catch (e: Exception) {
            AppResult.Failure("일괄 수정 요청 실패: ${e.message}")
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // 무시
        }
    }
}
