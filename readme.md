# 개인 미디어 관리 시스템 (Personal Media CLI)

본 프로젝트는 도서, 영화, 음악 등의 개인 미디어 컬렉션을 등록, 조회, 수정, 삭제, 정렬 및 통계 연산할 수 있는 **콘솔 기반의 미디어 라이브러리 애플리케이션**입니다. 

로컬 단일 실행 방식의 기존 구조에서 탈피하여, **소켓(Socket) 통신 기반의 멀티스레드 서버-클라이언트 아키텍처**로 개편되었으며, Windows PowerShell 환경에 최적화된 ASCII UI와 강력한 데이터 무결성 검증 기능을 포함하고 있습니다.

---

## 🏗️ 1. 시스템 아키텍처 (System Architecture)

본 시스템은 데이터 관리 및 비즈니스 로직을 처리하는 **중앙 데이터 서버(Server)**와 사용자가 터미널을 통해 상호작용하는 **프레젠테이션 클라이언트(Client)**로 물리적으로 분리되어 동작합니다.

```
┌──────────────────────────────────────┐          TCP Socket          ┌──────────────────────────────────────┐
│           MediaClient (UI)           │   ────────────────────────►  │          MediaServer (Port)          │
│                                      │   ◄────────────────────────  │                                      │
│  - User Mode (조회/검색/즐겨찾기)    │         Port: 50001          │  - Multi-threaded Handler            │
│  - Admin Mode (등록/수정/삭제)       │                              │  - Thread-safe MediaLibrary (Cache)  │
│  - CLI Rendering (ANSI Colors/cls)   │                              │  - MediaRepository (CSV Storage)     │
└──────────────────────────────────────┘                              └──────────────────────────────────────┘
                                                                                          │ Local I/O
                                                                                          ▼
                                                                              ┌──────────────────────┐
                                                                              │       data.csv       │
                                                                              └──────────────────────┘
```

### 소켓 통신 시퀀스 (Socket Communication Sequence)
1. **연결 및 인증**: 클라이언트가 서버(`localhost:50001`)에 소켓 연결을 수립한 후, `LOGIN USER` 또는 `LOGIN ADMIN <password>` 프로토콜을 보내 세션을 수립합니다.
2. **요청 처리**: 클라이언트가 요청(예: `GET_ITEMS`, `ADD`, `DELETE` 등)을 전송하면, 서버는 스레드 풀을 통해 요청을 격리하고 동기화 블록(`synchronized`) 내에서 데이터를 가공합니다.
3. **데이터 동기화 및 응답**: 데이터 변경 작업 시 서버는 즉시 메모리 상태를 수정하고 `data.csv` 파일에 반영한 뒤 클라이언트에 `SUCCESS` 또는 `ERROR` 응답을 전송합니다.

### 소켓 명령어 프로토콜 정의
| 명령어 | 인자 (Parameters) | 역할 | 서버 응답 포맷 |
| :--- | :--- | :--- | :--- |
| **`LOGIN`** | `USER` 또는 `ADMIN <pwd>` | 역할별 로그인 세션 생성 | `SUCCESS` 또는 `ERROR:<메시지>` |
| **`GET_ITEMS`** | 없음 | 전체 미디어 데이터 조회 | CSV 데이터 행(들) + `__END_OF_RESPONSE__` |
| **`ADD`** | `CSV 포맷 문자열` | 새로운 미디어 항목 추가 (Admin 전용) | `SUCCESS` or `ERROR:<메시지>` |
| **`DELETE`** | `ID (정수)` | 특정 미디어 항목 삭제 (Admin 전용) | `SUCCESS` or `ERROR:<메시지>` |
| **`UPDATE`** | `ID` + `CSV 포맷 문자열` | 특정 미디어의 상세 속성/즐겨찾기 수정 | `SUCCESS` or `ERROR:<메시지>` |
| **`BULK_UPDATE`**| `STATUS` + `장르명` | 특정 장르 항목의 상태 일괄 변경 (Admin 전용) | `SUCCESS <변경된 개수>` or `ERROR:<메시지>` |
| **`STATS`** | 없음 | 전체 데이터 통계 문자열 요청 | 통계 텍스트 + `__END_OF_RESPONSE__` |

---

## 📂 2. 프로젝트 디렉토리 구조 (Directory Structure)

```
c:\Users\daewa\OneDrive\Desktop\daewan\task\
├── data.csv                            # 미디어 데이터 저장 파일 (CSV 포맷)
├── readme.md                           # 프로젝트 상세 가이드 문서 (본 문서)
└── src/
    ├── Main.kt                         # 클라이언트 진입점 (접속 주소 입력 및 로그인 선택)
    └── com/mediaproject/
        ├── core/
        │   ├── model/
        │   │   └── MediaItem.kt        # 도메인 모델 (MediaItem sealed class, Book, Movie, Music)
        │   ├── library/
        │   │   └── MediaLibrary.kt     # 서버 비즈니스 로직 및 메모리 캐시 라이브러리
        │   ├── repo/
        │   │   ├── MediaRepository.kt  # CSV 파싱 및 파일 입출력 저장소
        │   │   └── AppResult.kt        # 함수형 예외 처리를 위한 성공/실패 Wrapper
        │   └── net/
        │       ├── MediaServer.kt      # 멀티스레드 기반 TCP 소켓 서버 (Port: 50001)
        │       ├── NetworkClient.kt    # 클라이언트 네트워크 통신 엔진
        │       └── TestClient.kt       # 15개 시나리오 자동 검증용 테스트 스위트
        └── ui/
            ├── UserApp.kt              # 일반 사용자 인터페이스 (조회, 검색, 정렬, 즐겨찾기 토글)
            └── AdminApp.kt             # 관리자 인터페이스 (추가, 상세수정, 삭제, 일괄상태변경)
```

---

## 🔍 3. 핵심 소스 코드 분석 (Source Code Analysis)

### 1) [MediaItem.kt](file:///c:/Users/daewa/OneDrive/Desktop/daewan/task/src/com/mediaproject/core/model/MediaItem.kt) (도메인 모델)
- `sealed class` 구조로 `MediaItem`을 정의하고 `Book`, `Movie`, `Music` 클래스가 이를 상속받아 미디어 특화 정보를 관리합니다.
- 객체의 불변성을 준수하면서 부분적 데이터를 교체할 수 있도록 복사 헬퍼 메서드(`withStatus`, `withFavorite`, `withBaseDetails`, `withMemoAndTags`)를 제공합니다.
```kotlin
// MediaItem.kt의 데이터 모델 예시 (Book)
data class Book(
    override val id: Int, 
    override val title: String, 
    override val genre: String, 
    override val rating: Double, 
    override val date: String, 
    override val memo: String, 
    override val status: Status, 
    override val isFavorite: Boolean, 
    override val tags: List<String>, 
    val author: String, 
    val publisher: String, 
    val pages: Int
) : MediaItem() {
    override fun typeName() = "BOOK"
    override fun toCsv() = "BOOK|$id|$title|$genre|$rating|$date|$memo|${status.name}|$isFavorite|${tags.joinToString(",")}|$author|$publisher|$pages"
    override fun toDisplayString() = "[책] #$id $title | $author 저 | 평점: $rating | $status"
}
```

### 2) [MediaServer.kt](file:///c:/Users/daewa/OneDrive/Desktop/daewan/task/src/com/mediaproject/core/net/MediaServer.kt) (소켓 서버)
- 서버 실행 시 `ServerSocket`을 바인딩하고 클라이언트 접근 시마다 `thread { handleClient(socket) }`를 통해 멀티스레드로 처리하여 응답 대기 지연을 예방합니다.
- 데이터 조회 및 수정 명령 호출 시 `synchronized(library)` 임계 구역(Critical Section)을 설정하여 다중 스레드 환경에서도 원자성과 일관성을 지킵니다.
- **인증 가드**: 클라이언트의 `role` 세션 상태를 추적하여 일반 사용자가 `ADD`, `DELETE`, `BULK_UPDATE` 같은 관리자 권한 명령을 시도하면 즉시 차단합니다.

### 3) [NetworkClient.kt](file:///c:/Users/daewa/OneDrive/Desktop/daewan/task/src/com/mediaproject/core/net/NetworkClient.kt) (클라이언트 소켓 엔진)
- 소켓 연결, 로그인, 통계 리포트 획득 및 데이터 변경 명령을 캡슐화한 클래스입니다.
- **다중 행 blocking 방지**: `GET_ITEMS`나 `STATS` 등의 응답 수신 시 첫 줄이 `ERROR:`로 시작하면 버퍼 루프에 진입하지 않고 실패 결과(`AppResult.Failure`)를 즉시 반환하여 클라이언트 터미널이 멈추는 동적 행 현상을 예방합니다.

### 4) [UserApp.kt](file:///c:/Users/daewa/OneDrive/Desktop/daewan/task/src/com/mediaproject/ui/UserApp.kt) (사용자 UI)
- 일반 사용자 화면을 렌더링하며 화면 정리(`clearScreen()`) 기능을 메뉴 진입점마다 장착해 쾌적한 화면 전환을 돕습니다.
- ID순, 제목순, 평점순, 날짜순 등 리스트 정렬 조회를 로컬 메모리 리스트 상에서 실시간으로 정렬하여 표시합니다.
- 일반 사용자 권한을 검증하는 서버의 `UPDATE` 규칙을 충족시키면서 즐겨찾기 상태를 안전하게 토글하도록 구현되어 있습니다.

### 5) [AdminApp.kt](file:///c:/Users/daewa/OneDrive/Desktop/daewan/task/src/com/mediaproject/ui/AdminApp.kt) (관리자 UI)
- 새 미디어를 대화형 폼으로 추가할 수 있습니다.
- **주입 공격 방지**: 사용자가 데이터 저장 구분자인 파이프(`|`) 기호를 제목, 메모, 태그 등에 기입하면 입력을 거부하고 경고하는 이스케이프 검증 로직이 포함되어 있습니다.
- **스마트 수정 지원**: 특정 ID 미디어 수정 시 기존 메타데이터 값을 디폴트 가이드로 제공하며, 값을 수정하지 않고 엔터를 치면 이전 값을 그대로 재사용합니다.

---

## 🛠️ 4. 주요 기술적 특징 (Key Features)

1. **PowerShell 인코딩 호환성 최적화**
   - Windows 터미널의 고질적인 유니코드 문자열 깨짐 현상을 해결하기 위해 UI 및 로그 상의 이모지를 모두 배제하고 ASCII 대괄호(`[*]`, `[ ]`, `[V]`)와 표준 한글 구분 기호(`[책]`, `[오류]`)로 변경하였습니다.
2. **함수형 에러 처리 (`AppResult`)**
   - 네트워크 장애, 파싱 실패, 파일 권한 부재 등의 예외 상황을 전통적인 `try-catch` 전파 방식 대신, 성공/실패 여부를 명시적으로 격리하는 `AppResult` sealed class 형태로 다루어 가독성과 프로그램 안정성을 극대화했습니다.
3. **일괄 처리 제어 (Bulk Update)**
   - 관리자가 변경할 대상 상태(`WISHLIST`, `IN_PROGRESS`, `DONE`)와 타겟 `장르`를 적어 일괄 수정을 명령하면, 서버에서 실시간 매칭을 돌려 디스크에 한 번에 플러시하고 변경 건수를 집계해 반환합니다.

---

## 🚀 5. 빌드 및 실행 방법 (How to Build & Run)

본 가이드는 인텔리제이(IntelliJ IDEA) 번들 Kotlin 컴파일러 및 Java가 설치된 Windows 환경을 기준으로 작성되었습니다.

### 1) 빌드 (JAR 파일 컴파일)
PowerShell 또는 CMD 콘솔을 열어 프로젝트 루트 디렉토리로 이동한 뒤, 아래 명령을 복사하여 입력합니다.
```powershell
# 1. 컴파일할 kotlin 파일 목록 추출
$files = (Get-ChildItem -Path src -Filter *.kt -Recurse | Select-Object -ExpandProperty FullName)

# 2. task.jar 파일로 컴파일 빌드
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.2\plugins\Kotlin\kotlinc\bin\kotlinc.bat" -include-runtime -d out/task.jar $files
```

### 2) 서버 실행 (MediaServer)
중앙에서 미디어 데이터 캐싱과 CSV 영속 파일 변경을 처리하는 소켓 서버를 켭니다. (Port: 50001)
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.2\plugins\Kotlin\kotlinc\bin\kotlin.bat" -cp out/task.jar com.mediaproject.core.net.MediaServerKt
```

### 3) 클라이언트 실행 (Main CLI)
실제 미디어를 조회/조작할 사용자 콘솔 클라이언트를 시작합니다.
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.2\plugins\Kotlin\kotlinc\bin\kotlin.bat" -cp out/task.jar MainKt
```

---

## 🧪 6. 자동화 검증 (Integration Verification)

구현한 소켓 서버-클라이언트 아키텍처와 로그인 권한 가드 기능을 수동 테스트 없이 즉시 자동 검증할 수 있는 통합 테스트 클라이언트가 포함되어 있습니다.

```powershell
# 통합 테스트 클라이언트 실행
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.2\plugins\Kotlin\kotlinc\bin\kotlin.bat" -cp out/task.jar com.mediaproject.core.net.TestClientKt
```

### 테스트 시나리오 동작 흐름:
1. **연결 수립** -> 2. **비로그인 요청 차단 검증** -> 3. **일반 유저 로그인** -> 4. **일반 유저 쓰기 권한 차단 검증** -> 5. **잘못된 비밀번호 관리자 차단 검증** -> 6. **정상 관리자 로그인** -> 7. **관리자 미디어 등록** -> 8. **조회 검증** -> 9. **미디어 상세 정보 수정** -> 10. **일괄 상태 변경(장르 대상)** -> 11. **미디어 삭제 및 정리** -> 12. **라이브러리 통계 수집**.