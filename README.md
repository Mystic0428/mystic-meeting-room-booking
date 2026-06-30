# 會議室預約系統 Backend Demo

> Java / Spring Boot 後端實作考題。提供會議室預約、退回審核、查詢統計等功能。

---

## 1. 專案啟動方式

### 方式 A:Docker Compose(推薦,一鍵起 app + db)

前置:安裝 Docker。於專案根目錄執行:

```bash
docker compose up --build
```

會自動 build app image、起 PostgreSQL、等它 healthy 後再起 app。看到 `Started MysticMeetingRoomBookingApplication` 即成功。

- API 入口:`http://localhost:8080`
- **所有 API(除 `/api/auth/login`)皆需 JWT**。範例使用者密碼一律 `password123`:

```bash
# 1) 登入取得 token
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"wang@example.com","password":"password123"}'
# → {"token":"eyJ...","tokenType":"Bearer","userId":1,"role":"USER"}

# 2) 帶 token 呼叫(回傳 5 間範例會議室)
curl localhost:8080/api/rooms -H "Authorization: Bearer <貼上 token>"
```

- **互動式 API 文件(Swagger UI)**:`http://localhost:8080/swagger-ui.html`(右上「Authorize」貼上 token 即可在 UI 測試)。

### 方式 B:本機開發(只用容器跑 DB,app 跑在本機)

```bash
docker compose up -d postgres     # 只起資料庫
./mvnw spring-boot:run            # app 連 application.yaml 的 localhost:5432
```

## 2. Docker Compose 使用方式

### 服務組成

| 服務 | 來源 | 說明 |
|---|---|---|
| `postgres` | 官方 `image: postgres:16` | 資料庫;具 `healthcheck`(`pg_isready`),含具名 volume 保存資料 |
| `app` | `build: .`(多階段 `Dockerfile`) | Spring Boot 應用;`depends_on` postgres 的 `service_healthy` 才啟動 |

- **app 連 DB 用服務名 `postgres`(非 `localhost`)**:compose 內網以服務名解析;連線設定由 `SPRING_DATASOURCE_*` 環境變數覆蓋 `application.yaml`(Spring relaxed binding)。
- **Dockerfile 為多階段建置**:build 階段用 JDK 編 jar(`-DskipTests`,因測試需 Docker/Testcontainers,不在 image build 內跑),runtime 階段只帶 JRE + jar → image 更小。

### 常用指令

```bash
docker compose up --build       # 重 build 並前景啟動(log 直接印在終端)
docker compose up -d --build    # 重 build 並背景啟動
docker compose logs -f app      # 追 app 的 log
docker compose ps               # 看容器狀態
docker compose down             # 停止並移除容器(DB 資料保留在 volume)
docker compose down -v          # 連 volume 一起刪 → DB 資料清空(下次重跑會重建 + 重灌初始資料)
```

> `-d` 控制前景/背景,`--build` 控制是否重新 build image——兩者獨立,可合併使用。

## 3. API 清單

所有路徑前綴 `/api`。**除 `/api/auth/login` 外,所有端點需帶 `Authorization: Bearer <jwt>`**(見下方「認證」)。錯誤一律由 `GlobalExceptionHandler` 統一回傳 `ApiError`(400 / 401 / 403 / 404 / 409 / 500)。

### 認證 Auth(JWT,加分項)

| Method | Path | 說明 | 成功碼 |
|---|---|---|---|
| POST | `/api/auth/login` | email + 密碼登入,回傳 JWT(`token` / `userId` / `role`) | 200 |

- 採 **self-issued JWT(HS256/384)**,stateless;token 內含 `sub=userId` 與 `role` claim。
- `cancel-request` / `review` 的操作者身分**取自 token**,不再由 request body 帶入。
- 未帶 / 無效 token → **401**;已驗證但角色不足(如非 REVIEWER/ADMIN 審核)→ **403**。

### 會議室 Room

| Method | Path | 說明 | 成功碼 |
|---|---|---|---|
| POST | `/api/rooms` | 新增會議室 | 201 |
| GET | `/api/rooms` | 會議室列表 | 200 |
| GET | `/api/rooms/{id}` | 單一會議室 | 200 |
| PUT | `/api/rooms/{id}` | 修改會議室 | 200 |
| DELETE | `/api/rooms/{id}` | **停用**會議室(軟刪除) | 204 |

### 使用者 User

| Method | Path | 說明 | 成功碼 |
|---|---|---|---|
| POST | `/api/users` | 新增使用者(email 不可重複) | 201 |
| GET | `/api/users` | 使用者列表 | 200 |
| GET | `/api/users/{id}` | 單一使用者 | 200 |

### 預約 Reservation(寫入)

| Method | Path | 說明 | 成功碼 |
|---|---|---|---|
| POST | `/api/reservations` | 建立預約 | 201 |
| POST | `/api/reservations/{id}/cancel-request` | 本人申請退回 | 200 |
| POST | `/api/reservations/{id}/review` | REVIEWER / ADMIN 審核 | 200 |

### 預約查詢 Query

| Method | Path | 說明 |
|---|---|---|
| GET | `/api/reservations` | 預約總覽;查詢參數 `dateFrom` `dateTo` `roomId` `roomName` `username` `status` + 分頁排序 `page` `size` `sort` |
| GET | `/api/rooms/{roomId}/reservations` | 依會議室查所有預約 |
| GET | `/api/users/{userId}/reservations?status=` | 依使用者查所有預約(可選 `status` 篩選,依 startTime 排序)|
| GET | `/api/reservations/timeline?date=YYYY-MM-DD` | 每日時段表(只列 approved,啟用中的房皆出現) |
| GET | `/api/reservations/monthly-summary?year=&month=` | 每月各狀態統計 + 明細 |
| GET | `/api/rooms/top-used?year=&month=` | 使用率最高前三會議室 |
| GET | `/api/reservations/export?year=&month=` | 匯出某月預約為 CSV(`text/csv` 附件下載,含審核者/審核時間)|

## 4. Database Schema 說明

Schema 由 Flyway migration 管理(`src/main/resources/db/migration`,V1–V9),`ddl-auto: validate` 只驗證不自動建表。

### 資料表

| 表 | 用途 | PK | FK | UNIQUE | 其他約束 |
|---|---|---|---|---|---|
| `users` | 使用者 / 員工資料 | `id` | — | `email` | — |
| `rooms` | 會議室資料(`is_active` 做軟停用) | `id` | — | `name` | — |
| `reservations` | 預約主檔 | `id` | `room_id → rooms`、`user_id → users` | — | `CHECK (start_time < end_time)`、`CHECK (attendee_count > 0)`、exclusion constraint(見第 6.3 節) |
| `reservation_reviews` | 審核 / 退回申請的稽核紀錄(每次審核一筆) | `id` | `reservation_id → reservations`、`reviewer_id → users` | — | — |

### 完整欄位

**`users`**

| 欄位 | 型別 | 約束 / 說明 |
|---|---|---|
| `id` | BIGINT | PK,identity |
| `username` | VARCHAR(50) | NOT NULL |
| `email` | VARCHAR(100) | NOT NULL,UNIQUE |
| `department` | VARCHAR(50) | |
| `role` | VARCHAR(50) | NOT NULL,enum STRING(USER / REVIEWER / ADMIN)|
| `password_hash` | VARCHAR(100) | 可為 null;BCrypt 雜湊(JWT 加分,V9)|
| `created_at` / `updated_at` | TIMESTAMP | JPA Auditing 自動填 |

**`rooms`**

| 欄位 | 型別 | 約束 / 說明 |
|---|---|---|
| `id` | BIGINT | PK,identity |
| `name` | VARCHAR(100) | NOT NULL,UNIQUE |
| `capacity` | INTEGER | NOT NULL,CHECK (capacity > 0) |
| `floor` | VARCHAR(50) | |
| `location` | VARCHAR(100) | |
| `is_active` | BOOLEAN | NOT NULL,DEFAULT TRUE(軟刪除標記)|
| `created_at` / `updated_at` | TIMESTAMP | JPA Auditing |

**`reservations`**

| 欄位 | 型別 | 約束 / 說明 |
|---|---|---|
| `id` | BIGINT | PK,identity |
| `room_id` | BIGINT | NOT NULL,FK → `rooms(id)` |
| `user_id` | BIGINT | NOT NULL,FK → `users(id)` |
| `start_time` | TIMESTAMP | NOT NULL |
| `end_time` | TIMESTAMP | NOT NULL |
| `subject` | VARCHAR(200) | NOT NULL |
| `purpose` | VARCHAR(500) | |
| `attendee_count` | INTEGER | NOT NULL,CHECK (attendee_count > 0) |
| `status` | VARCHAR(30) | NOT NULL,enum STRING |
| `cancel_reason` | VARCHAR(500) | 可為 null(V7)|
| `created_at` / `updated_at` | TIMESTAMP | JPA Auditing |

> 表級約束:`CHECK (start_time < end_time)`、exclusion constraint `no_overlapping_reservation`(V4,見第 6.3 節)。

**`reservation_reviews`**

| 欄位 | 型別 | 約束 / 說明 |
|---|---|---|
| `id` | BIGINT | PK,identity |
| `reservation_id` | BIGINT | NOT NULL,FK → `reservations(id)` |
| `reviewer_id` | BIGINT | NOT NULL,FK → `users(id)` |
| `action` | VARCHAR(20) | NOT NULL,enum STRING(APPROVED / REJECTED)|
| `comment` | VARCHAR(500) | |
| `reviewed_at` | TIMESTAMP | 審核時間 |
| `created_at` / `updated_at` | TIMESTAMP | JPA Auditing |

### Entity 關聯(對應題目 ORM 要求)

```
Reservation       多對一  Room
Reservation       多對一  User
ReservationReview 多對一  Reservation
ReservationReview 多對一  Reviewer(User)
```

全部採**單向 `@ManyToOne(fetch = LAZY)`**:從「多」的一方持有外鍵即可滿足所有查詢,不在 Room / User 開 `@OneToMany` 集合,避免不必要的雙向關聯與載入。

### Index

詳見第 7 節。重點:FK(`room_id`、`user_id`)PostgreSQL 不自動建索引,於 V8 手動補上;重疊衝突由 V4 的 GiST exclusion index 服務。

### Reservation status 的設計原因

`status` 在 DB 用 `VARCHAR(30)`、Java 用 `@Enumerated(EnumType.STRING)` 對應 enum,**刻意不用 `ORDINAL`**:

1. **避免靜默資料損毀**:`ORDINAL` 存位置序號(0,1,2…),日後在 enum 中間插入或重排狀態,會使既有資料列的數字對應到**不同**的狀態,且不會報錯。`STRING` 存名稱(`'APPROVED'`),不受宣告順序影響。
2. **可讀 / 可查 / 可除錯**:DB 直接看得懂,SQL 可寫 `WHERE status = 'APPROVED'`。
3. **與本專案一致**:exclusion constraint 與查詢使用 `status IN ('PROCESSING','APPROVED','CANCEL_REQUESTED')` 等字面值,正是因為存 STRING 才乾淨可讀。

**取捨**:STRING 每列多耗少量空間、比較略慢,但 status 為低基數欄位,差異可忽略,安全與可讀性遠勝。

**狀態集合**(題目要求至少 `processing`/`approved`/`rejected`,其餘為自行擴充):

```
PROCESSING       → 已建立、等待審核(佔用時段)
APPROVED         → 審核通過(佔用時段)
REJECTED         → 審核駁回(不佔用)
CANCEL_REQUESTED → 已申請退回、等待審核(仍佔用,理由見第 12 節)
CANCELLED        → 退回已核准(不佔用)
```

## 5. 預約衝突判斷邏輯

一筆預約是否衝突,由三個條件同時成立:**同一會議室** + **時間重疊** + 對方處於 **「佔用中」狀態**。

### 佔用中狀態

依題目「預約規則」第 8–10 條,並補上自訂的 `cancel_requested`:

| 狀態 | 是否佔用 | 依據 |
|---|---|---|
| `PROCESSING` | ✅ 佔用 | 題目規則 10 |
| `APPROVED` | ✅ 佔用 | 題目規則 10 |
| `CANCEL_REQUESTED` | ✅ 佔用 | 題目未定義,**自訂**(理由見第 12 節「情境 2」) |
| `REJECTED` | ❌ 不佔用 | 題目規則 8 |
| `CANCELLED` | ❌ 不佔用 | 題目規則 9 |

### 時間重疊判斷

採題目給定公式:

```
existing.start_time < new_end_time
AND existing.end_time > new_start_time
```

採**嚴格不等號**,因此**邊界相接不算衝突**——例如 `10:00–11:00` 與 `11:00–12:00` 可同室連續預約(back-to-back)。

### 兩層防線

| 層級 | 機制 | 目的 |
|---|---|---|
| 應用層 | 建立前以 `existsOverlapping` 查詢,命中即回 **409** | 友善、明確的錯誤訊息 |
| DB 層 | PostgreSQL **exclusion constraint** | 併發競態下的最後防線(詳見第 6 節 Transaction / Lock 設計) |

衝突時回 `409 Conflict`,錯誤格式由 `@RestControllerAdvice` 全域例外處理器統一產生。

## 6. Transaction / Lock 設計說明

### 6.1 `@Transactional` 的使用

寫入操作標 `@Transactional`,查詢標 `@Transactional(readOnly = true)`。

| Method | 交易 | 為什麼 |
|---|---|---|
| `ReservationService.create` | `@Transactional` | 驗證讀取 + INSERT 是同一致性單元;DB constraint 擋下時整筆回滾 |
| `ReservationService.requestCancellation` | `@Transactional` | 改狀態靠 dirty checking,須在交易內才會 flush |
| `ReservationService.review` | `@Transactional` | **兩個寫入**(改預約狀態 + 插 `ReservationReview` 紀錄)須原子化:要嘛全成功、要嘛全失敗 |
| 各查詢(overview / timeline / monthly-summary / top-used) | `@Transactional(readOnly = true)` | 純讀;Hibernate 關閉自動 flush、跳過 dirty-checking 開銷,也表達意圖(可路由讀庫) |

兩個關鍵點:

- **`@Transactional` 不只為了 rollback,也界定 persistence context 的範圍**,讓 dirty checking 在 commit 時自動 flush——這就是為什麼 `requestCancellation` / `review` 改了狀態卻**不用呼叫 `save()`**(entity 在交易內是 managed 狀態)。
- **`review()` 是最典型的原子性案例**:狀態更新與審核紀錄兩個寫入必須同生共死。

**Rollback 行為**:Spring 預設**只對 unchecked(`RuntimeException` / `Error`)rollback,checked exception 不會**。本專案所有自訂例外都 `extends RuntimeException`,因此任一驗證 / 衝突 / 授權失敗都會觸發 rollback;`create()` 內 `catch DataIntegrityViolationException` 後重新丟出的 `ReservationConflictException` 也是 `RuntimeException`,一樣回滾。

### 6.2 Lazy Loading 與 N+1

關聯一律 **LAZY + 單向 `@ManyToOne`**(`Reservation → Room`、`Reservation → User`)。為避免組 DTO 時觸發 N+1,依查詢特性用四種手段:

| 手段 | 用在 | 機制 |
|---|---|---|
| **JOIN FETCH** | `findByRoomIdWithDetails`、`findApprovedByDateWithUser`、`findByMonthWithDetails`(固定形狀、無分頁) | 一條 SQL 把 room/user 一起 join 回來 |
| **Batch fetch**(`default_batch_fetch_size=100`) | `overview`(Specification + 分頁) | 翻頁後存取關聯時,以 `WHERE id IN (?,?,…)` 一次批次載入(最多 100) |
| **讀 FK 不觸發 lazy** | `timeline` 的 `groupingBy(r -> r.getRoom().getId())` | LAZY proxy 已握有外鍵 id,讀 id 不發查詢 |
| **投影 / native query** | `top-used`(`TopUsedRoomProjection`) | 不載入 entity,N+1 結構上不可能發生 |

**為什麼 `overview` 不用 JOIN FETCH?** JOIN FETCH 與分頁無法並存——Hibernate 無法在 SQL 同時做 join fetch 與 limit/offset,只能把整批撈進記憶體再分頁(吐 `HHH000104` 警告,資料量大就爆)。故**固定查詢用 JOIN FETCH,分頁查詢改用 batch fetch**。(其他可選但本專案未用:`@EntityGraph`、DTO constructor expression。)

**`open-in-view: false`(刻意設定)**:Spring 預設 `true`,會把 persistence context 開到 view 渲染完,使 lazy 關聯在序列化 JSON 時才載入——這會**偷偷藏 N+1**,也多佔 DB 連線。設 `false` 後,所有載入必須在 service 的交易內完成;若忘了 fetch 又去碰 lazy 欄位,會立刻 `LazyInitializationException` **fail-fast**,在開發期就抓到,而非上線後默默變慢。

### 6.3 併發控制

**問題**:兩位使用者同時預約同房同時段,如何避免兩筆都成功?

**① 選用:PostgreSQL exclusion constraint**(`GiST` + `btree_gist`)。

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations
    ADD CONSTRAINT no_overlapping_reservation
    EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_time, end_time) WITH &&
    )
    WHERE (status IN ('PROCESSING', 'APPROVED', 'CANCEL_REQUESTED'));
```

搭配兩層防線:應用層先以 `existsOverlapping` 查詢(命中回友善 409),DB 層由上述 constraint 做最後防線。**應用層檢查存在 TOCTOU(check-then-act)race**——查詢到 INSERT 之間仍可能被插入,所以它只負責使用者體驗,**真正保證正確性的是 DB constraint**。`create()` 用 `saveAndFlush` 強制即刻 INSERT,competition 下 constraint 擋下時 catch `DataIntegrityViolationException` → 轉 409。

**② 為什麼選它(而非其他三種):**

| 選項 | 為什麼不選 |
|---|---|
| Pessimistic Lock | 新預約還沒有那一列可鎖,得鎖整個 room 列 / advisory lock → 把同房所有預約序列化,程式更複雜 |
| Optimistic Lock(`@Version`) | 防的是「同一列」被並發 UPDATE;但這裡是「兩筆不同的 INSERT 剛好時間重疊」,沒有共享版本欄位可撞 → 救不到 |
| Application-level lock | 只在單一 JVM 內有效,水平擴展成多實例就破功 |
| **PostgreSQL exclusion constraint** ✅ | 在 DB(唯一真相來源)強制執行,不管幾個 app 實例都安全;原生處理「不同列但時間重疊」 |

**③ 限制:**

1. **綁死 PostgreSQL**:需 `GiST` + `btree_gist`,換 MySQL 等就得改悲觀鎖 / 應用層序列化。
2. **業務規則雙寫**:「哪些狀態算佔用」同時存在 Java(`OCCUPYING_STATUSES`)與 constraint 的 `WHERE`,改一邊忘了另一邊就會邏輯打架。本專案以測試守同步——`ReservationRepositoryTest`(情境 9–12)+ 併發測試一旦兩邊不一致就會紅。
3. **錯誤較生硬**:DB 丟泛型 `DataIntegrityViolationException`,需自行 catch 翻成 409;且為「直接拒絕」,無內建排隊 / 重試。

**④ 流量變大如何調整:**

- **不重疊的預約不會互相阻塞**:constraint 只在「同房 + 時間真的重疊」時衝突,不同房或同房不同時段完全平行 INSERT。唯一競爭的是「同房同時段」,而那本就只能成功一筆 → 寫入路徑天然可水平擴展(不像悲觀鎖會序列化同房所有預約)。
- **熱門會議室**:app 層 `existsOverlapping` 預檢可在 DB 寫入前洩掉大部分注定失敗的請求(load shedding);要讓搶輸者最終訂到,可加退避重試 / 排隊。
- **資料量達百萬筆**:reservations 依 `start_time` 按**月份做 range partition**——查詢多帶月份範圍,觸發 partition pruning 只打單一子表,per-partition 的 btree / GiST index 也更小更快;讀查詢(`readOnly`)可分流到讀庫;查詢條件欄位另建 btree index(見第 7 節)。

## 7. Index 設計說明

### ① 建立了哪些 index

| Index | 欄位 | 來源 | 類型 |
|---|---|---|---|
| PK | 各表 `id` | 主鍵 | btree(自動) |
| `users.email`、`rooms.name` | — | UNIQUE 約束 | btree(自動) |
| `no_overlapping_reservation` | `room_id` + `tsrange(start_time, end_time)`,partial(佔用狀態) | V4 exclusion constraint | **GiST** |
| `idx_reservations_room_id_start_time` | `(room_id, start_time)` | V8 | btree |
| `idx_reservations_start_time` | `(start_time)` | V8 | btree |
| `idx_reservations_user_id` | `(user_id)` | V8 | btree |

> ⚠️ **PostgreSQL 不會自動為 foreign key 建 index**(只有 PK / UNIQUE 會自動建),因此 `room_id`、`user_id` 需在 V8 手動補上。

### ② 為什麼建這些 / ③ 哪些查詢會用到

| Index | 為什麼 | 用到的查詢 |
|---|---|---|
| GiST(V4) | 範圍重疊(`&&`)只能靠 GiST,btree 做不到 | `existsOverlapping`(衝突檢查) |
| `(room_id, start_time)` | **等值欄位(`room_id`)在前、範圍/排序欄位(`start_time`)在後**——可同時過濾 room 並直接依 start_time 有序輸出,免額外排序 | `findByRoomIdWithDetails`、依會議室查預約 |
| `(start_time)` | 只篩時間、不帶 room 的查詢無法用上面的複合索引(`start_time` 非前導欄位),故另建單欄 | `timeline`(某日)、`monthly-summary`、`top-used`、`overview` 的日期區間 |
| `(user_id)` | FK 無自動索引,查某使用者的預約會全表掃 | 查某位 user 的所有預約 |

### ④ 資料量增加到 100 萬筆,會如何調整

- **分區(partitioning)**:reservations 依 `start_time` 按**月份做 range partition**;查詢多帶月份範圍 → 觸發 **partition pruning** 只打單一子表,per-partition 的 btree / GiST index 更小更快;舊月份可 `DETACH` 封存。
- **BRIN index**:時間序列資料的 `start_time` 與實體寫入順序高度相關,改用 **BRIN** 索引體積遠小於 btree、且對範圍查詢有效,適合超大且依時間遞增的表。
- **讀寫分流**:查詢皆為 `@Transactional(readOnly = true)`,可路由到讀庫,主庫專責寫入。
- **複合 / partial index 微調**:依實際慢查詢(`EXPLAIN ANALYZE`)再決定是否加 `(status, start_time)` 之類的複合索引,而非預先全建。

### ⑤ 哪些欄位不適合盲目建 index

- **`status`(單欄)**:只有 5 種值、且大多為 `approved`,**選擇性太低**,planner 通常會略過改走全表掃;單獨建只是徒增寫入成本與空間。需要時用**複合索引**(`status` 搭 `start_time`)或 **partial index**(`WHERE status = 'APPROVED'`)更划算。
- **通則**:每多一個 index,每次 INSERT / UPDATE 都要多維護一份 → **寫入變慢、空間變大**。低基數欄位、很少出現在 `WHERE` 的欄位、或已被既有複合索引前導涵蓋的欄位,都不該盲目加。

## 8. 如何執行測試

```bash
./mvnw test       # 49 個測試(unit + repository + controller + 整合/併發),需 Docker(Testcontainers)
./mvnw verify     # 額外強制 Service 層 line 覆蓋率 ≥ 70%(JaCoCo check)
```

覆蓋率報告:`target/site/jacoco/index.html`(Service 層 line 覆蓋率 95%)。

### 測試類型(共 49 個)

| 類型 | 類別 | 技術 | 驗什麼 |
|---|---|---|---|
| Unit | `ReservationServiceTest`、`RoomServiceTest`、`UserServiceTest`、`ReservationQueryServiceTest` | JUnit 5 + Mockito | Service 商業邏輯(mock repository),不碰 DB、跑很快 |
| Repository | `ReservationRepositoryTest` | `@DataJpaTest` + Testcontainers(真 Postgres) | `existsOverlapping` 的查詢行為(status 過濾、room 範圍) |
| Controller | `ReservationControllerTest` | `@WebMvcTest` + MockMvc + `@MockitoBean` | HTTP 請求 → 狀態碼 + JSON,含例外 → 狀態碼對應 |
| 整合 + 併發 | `ReservationIntegrationTest` | `@SpringBootTest` + Testcontainers | 端到端建立 / 衝突;**兩條 thread 搶同房同時段 → 只成功一筆** |

> 全部測試**自帶 Testcontainers**,不依賴外部 Postgres;執行需本機有 Docker。

## 9. 已完成項目與未完成項目

### 已完成

- 會議室 / 使用者 CRUD(會議室為軟停用)
- 建立預約:完整驗證(存在性、起訖時間、不可過去、30 分鐘單位、容量、衝突)
- **兩層併發防線**(應用層檢查 + DB exclusion constraint),並以併發測試證明只成功一筆
- 退回申請 + 審核流程(以 domain method 實作狀態機)
- 五支查詢 API(總覽分頁/篩選/排序、依會議室、每日時段、每月統計、使用率前三)
- 全域例外處理(400 / 403 / 404 / 409 / 500)+ 統一 `ApiError`
- Bean Validation(`@RequestBody` 與 `@RequestParam` 兩種)
- Flyway 管理 schema(V1–V9)+ 初始資料(5 使用者 / 5 會議室 / 12 預約)
- 測試:unit / repository(Testcontainers)/ controller / 整合 / 併發;JaCoCo Service 層 95%,並以 `check` 強制 ≥ 70%
- Docker Compose 一鍵啟動(app + db)
- **Spring Security + JWT 身分驗證(加分)**:登入簽發 token、stateless 驗證、角色授權(REVIEWER/ADMIN 才能審核),操作者身分取自 token
- **Swagger / OpenAPI 文件(加分)**:springdoc 自動掃描 controller 產生,Swagger UI 於 `/swagger-ui.html`,各端點以 `@Tag` 分組、`@Operation` 加上說明,含 Bearer「Authorize」可直接測試受保護 API
- **CI(GitHub Actions,加分)**:push / PR 自動跑 `./mvnw verify`(測試 + 覆蓋率門檻),並上傳 JaCoCo 報告
- **匯出報表 CSV(加分)**:`GET /api/reservations/export`,含審核者/審核時間,加 UTF-8 BOM 讓 Excel 正確顯示中文

### 未完成 / 未實作(多為題目加分項)

- 公司開放時間 08:00–20:00 限制(Validation 加分)
- 單次預約時長上下限(最短 30 分 / 最長 4 小時)(Validation 加分)
- Audit Log(加分情境)
- 報表匯出 Excel 格式(目前為 CSV)

## 10. 可以改善的地方

- **大資料量擴展**:依第 6.3 / 第 7 節規劃導入月份分區、讀寫分流;依 `EXPLAIN ANALYZE` 結果微調複合 / partial index。
- **併發體驗**:對被擋下的衝突請求加入退避重試 / 排隊,讓搶輸者最終仍可訂到釋出的時段。
- **Validation 補強**:加上公司營業時間(08:00–20:00)與單次預約時長上下限。
- **JWT 強化**:加入 refresh token 與撤銷(黑名單)機制。
- **稽核 / Excel**:Audit Log 記錄關鍵操作;報表匯出可再支援 Excel 格式(目前為 CSV)。

## 11. Design Decisions

回答題目指定的 7 個問題。

### Q1. 為什麼這樣設計 Room / User / Reservation / ReservationReview 資料表?

- `rooms` / `users` 為主檔;`reservations` 為預約主檔,以 `@ManyToOne` 持有 `room_id` / `user_id` 外鍵(Reservation 多對一 Room、多對一 User)。
- **`reservation_reviews` 獨立成稽核表**(多對一 Reservation、多對一 Reviewer):一筆預約生命週期會被審核多次(新訂單審核、退訂審核),用欄位只能存最後一次;獨立表是 append-only 軌跡(誰 / 何時 / 動作 / 意見),`reservations` 只保「當前狀態」。
- `created_at` / `updated_at` 放 `@MappedSuperclass` 的 `BaseEntity`,由 JPA Auditing 自動填(DRY、不會忘)。
- 關聯一律**單向 `@ManyToOne(LAZY)`**:從「多」方持有 FK 已足夠,不開雙向 `@OneToMany`。

### Q2. Reservation status 為什麼這樣設計?

用 `@Enumerated(EnumType.STRING)` 存字串而非 `ORDINAL`:避免 enum 重排 / 插入時把既有資料對應到錯誤狀態(靜默損毀),且 DB 可讀、SQL 可寫 `status = 'APPROVED'`。五個狀態涵蓋建立 → 審核 → 退訂的完整生命週期。(詳見第 4 節)

### Q3. 為什麼 rejected / cancelled 的預約不應阻擋新預約?

它們是**終結 / 非生效**狀態,該時段已釋放、不再佔用(題目規則 8、9)。只有「佔用中」狀態(processing / approved / cancel_requested)才納入衝突判斷。

### Q4. 如何判斷兩筆預約時間是否重疊?

`existing.start < new.end AND existing.end > new.start`(嚴格不等號 → 邊界相接不算衝突)。(詳見第 5 節)

### Q5. 你選擇在哪一層處理商業邏輯?為什麼?

- **Service 層負責編排**(載入、授權、業務驗證、寫入);**狀態不變式封裝在 entity 的 domain method**(`approve()` / `requestCancel()`…,無 public setter,非法轉移丟例外)。
- Controller 只收發與回狀態碼、Repository 只查詢。
- 為什麼:單一職責、邏輯不散落 Controller、entity 自顧一致性、好測試。

### Q6. 哪些 API 有使用 transaction?為什麼?

寫入(`create` / `requestCancellation` / `review`)用 `@Transactional`——原子性,並讓 dirty checking 在 commit 自動 flush;查詢用 `@Transactional(readOnly = true)`。`review()` 同時改狀態 + 寫審核紀錄,是最典型的「要嘛全成功」案例。(詳見第 6.1 節)

### Q7. 如果重新設計一次,會修改哪三個地方?

> 註:原本的「改用 JWT 授權」已於加分項實作完成(見第 3 節「認證」與第 12 節「其他假設」)。以下為現階段仍想改進的三點:

1. **佔用狀態單一來源**:目前佔用中狀態在 Java(`OCCUPYING_STATUSES`)與 DB constraint 雙寫,理想上由單一來源衍生,避免改一邊忘另一邊。
2. **補上 Validation 加分項**:公司營業時間(08:00–20:00)與單次預約時長上下限(目前未實作)。
3. **JWT refresh / 撤銷**:目前 access token 無 refresh token、無黑名單,正式環境應補上續期與撤銷機制。

### 其他工程決策

- **DTO(`record` + 靜態 `from()`,不外露 entity)**:API 合約與持久層解耦,避免序列化 entity 的 lazy-loading 與過度暴露問題。
- **Command-Query 分離**:寫入在 `ReservationService`、查詢在 `ReservationQueryService`(查詢端統一 `readOnly`)。
- **例外 → HTTP 集中對應**:`GlobalExceptionHandler`(`@RestControllerAdvice`)統一輸出 `ApiError`,Service 只丟語意化例外。

## 12. Requirement Clarification(需求釐清)

題目以下列情境保留模糊空間,以下為我的假設與處理。

### 情境 1:預約時間邊界(A 訂 10:00–11:00,B 訂 11:00–12:00)

- **是否衝突:不算。** 可連續預約(back-to-back)。
- **如何判斷**:`existing.start < new.end AND existing.end > new.start`(嚴格不等號),邊界相接(`end == start`)不重疊。
- **如何測試**:repository test 放一筆 10–11 approved,查 11–12 → 不衝突;另查 10:30–11:30 → 衝突。

### 情境 2:`cancel_requested` 是否佔用

- **仍佔用**;審核通過前他人不能預約同時段。
- **理由**:退訂若被駁回會變回 `approved`,中途釋放時段將造成同時段雙重 approved。

### 情境 3:會議室停用

- **已存在的未來預約**:不自動取消(保留歷史、不波及既有預約)。
- **新預約**:禁止(`create()` 檢查 `isActive`,停用房回 400)。
- **Timeline**:不顯示停用房(只列啟用中)。
- **系統如何處理**:採軟刪除(`is_active = false`)而非物理刪除——room 被 `reservations.room_id` 以 `NOT NULL` FK 參照,硬刪會被 `RESTRICT` 擋下或(若 `CASCADE`)毀掉歷史預約。

### 情境 4:容量超過(容量 10,attendeeCount = 12)

- **直接拒絕(400)**,不建立 processing 等待審核。
- **理由**:容量是硬性物理限制,不會因審核而改變,沒有「等待審核」的意義;且及早回饋使用者。

### 其他假設(題目未明確定義)

- **新預約審核與退訂審核共用 `POST /api/reservations/{id}/review`**,依預約當下狀態分派:`processing` → 審新訂單(approve→approved / reject→rejected);`cancel_requested` → 審退訂(approve→cancelled / reject→退回 approved);其他狀態回 409。
- **身分驗證機制**:題目未要求登入(JWT 屬加分項)。**已於加分項實作**:`cancel-request` / `review` 的操作者身分改由 **JWT / `SecurityContext`** 取得(退回比對是否本人、審核需 `REVIEWER`/`ADMIN`),不再信任 request body。(`create` 仍保留 body `userId` 表示「為誰預約」,屬刻意取捨。)
- **端點授權(RBAC)取捨**:依規格角色表逐端點綁定,並區分「明文指派」與「模糊地帶」:
  - **明文照做**:會議室管理(`POST/PUT/DELETE /api/rooms`)→ `ADMIN`;審核(`/review`)→ `REVIEWER`/`ADMIN`;**匯出報表(`/export`)→ `ADMIN`**(角色表僅 ADMIN 列有「匯出報表」)。
  - **建立使用者(`POST /api/users`)**:規格未指派角色,且 ADMIN 能力清單未含此項,故**開放給已登入者**。已知風險:`UserRequest` 可指定 `role`,理論上可建立 ADMIN 帳號;正式環境應改為 ADMIN 管理,或拆出強制 `role=USER` 的自助註冊端點。
  - **月統計 / 使用率(`monthly-summary` / `top-used`)**:規格未明指角色,屬全域統計;**假設審核者需統計輔助判斷,故開放 `REVIEWER`/`ADMIN`**。一般 `USER` 僅能查看自己的預約,故擋下(403)。

## 13. Database Reasoning(資料庫設計理由)

### 預約衝突 SQL

判斷某房在 `[newStart, newEnd)` 是否與既有預約衝突(JPQL,`existsOverlapping`):

```sql
SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
FROM ReservationEntity r
WHERE r.room.id = :roomId
  AND r.status IN :occupyingStatuses      -- PROCESSING / APPROVED / CANCEL_REQUESTED
  AND r.startTime < :newEnd
  AND r.endTime   > :newStart
```

1. **為什麼這樣能判斷區間重疊**:兩區間「不重疊」只有兩種——新的整段在舊的之前(`new.end <= existing.start`)或之後(`new.start >= existing.end`)。將兩者否定(De Morgan)即得「重疊」= `existing.start < new.end AND existing.end > new.start`。
2. **為什麼不能只判斷 startTime 是否相同**:那只抓「同時開始」,會漏掉部分重疊——例如 10:00–11:00 與 10:30–11:30 起點不同卻確實重疊。
3. **rejected / cancelled 如何排除**:用 `status IN (佔用中狀態)` 過濾,只把 processing / approved / cancel_requested 納入;rejected / cancelled 不在集合內,自然不擋。

### Index 設計

詳見第 7 節。摘要:

- **建立**:`(room_id, start_time)`、`(start_time)`、`(user_id)`;衝突檢查由 V4 的 GiST exclusion index 服務。
- **100 萬筆時哪些 API 最可能變慢**:帶日期區間掃描的 timeline / monthly-summary / overview;以月份分區 + 適當 index 緩解。
- **如何用 `EXPLAIN ANALYZE` 檢查**:看查詢是否走 index(Index Scan vs Seq Scan)、估算列數與實際耗時是否吻合。
- **哪些欄位不宜盲建**:`status` 單欄(只有 5 種值、選擇性低,planner 多半略過)。

### Group By 查詢(monthly summary)

- **如何依 status 統計數量**:`GROUP BY status` + `COUNT(*)`,統計交給 DB,不在 Java 迴圈數;再把未出現的狀態補 0,確保輸出格式一致。
- **如何查詢指定月份**:`start_time >= :monthStart AND start_time < :monthEnd`(半開區間,避免月底邊界誤差)。
- **日期區間用 `start_time` 還是 `created_at`?用 `start_time`**:統計關心「會議**發生**在哪個月」,而非「預約**何時被建立**」。用 `created_at` 會把「這個月建立、下個月才開」的會議算錯月份。

## 14. AI Usage Statement

本專案開發過程使用 AI 輔助工具(Claude / Claude Code),方式如下:

- **設計討論與需求釐清**:由本人**先提出架構與設計方向**,再以問答方式逐步討論可優化之處(如題目模糊的 `cancel_requested` 是否佔用、審核流程雙用途、併發控制選型、index 設計);**最終設計決策與實作由我執行**。
- **概念學習**:用於理解或複習 PostgreSQL exclusion constraint、JPA 關聯與 N+1、`@Transactional` 與 rollback、Docker 多階段建置等觀念。
- **樣板協助**:部分 entity / DTO / 測試樣板與 README 草稿由 AI 協助產生,本人逐一檢視、調整。
- **發現並修正 AI 的錯誤**:AI 一度將「processing 是否佔用時段」誤判為題目未定義的釐清點;我對照題目 PDF 後確認題目已明訂 `processing` / `approved` 視為佔用(規則 10),予以更正,並重新釐清真正未定義的是 `cancel_requested`。
