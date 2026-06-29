# DEBUG NOTES

針對題目提供的三個 Debug 情境說明排查思路;附錄記錄本專案開發中實際遇到的問題。

---

## 情境 1:重複預約(偶爾兩人成功預約同房同時段)

**1. 先檢查哪些程式?**
建立預約的衝突檢查邏輯:① 是「只比 startTime 相同」還是「真的判斷區間重疊」?② 衝突檢查是否只在應用層(查詢 → 判斷 → 寫入)?③ DB 端是否有任何約束兜底?

**2. 查哪些資料表?**
`reservations`——找同一 `room_id` 下、時間區間重疊、且狀態為佔用中(processing/approved/cancel_requested)的多筆資料;並確認表上有無 exclusion / unique 約束。

**3. 如何重現?**
這是併發競態,單純手點不易重現。用兩條 thread **同時**送同房同時段請求:`ExecutorService` + `CountDownLatch`(起跑閘讓兩條同時觸發),觀察是否兩筆都成功。

**4. 如何修正?**
- 修正重疊判斷為區間公式:`existing.start < new.end AND existing.end > new.start`。
- **加 DB exclusion constraint 當最後防線**——應用層「查詢→寫入」存在 TOCTOU race,兩條 thread 可同時通過檢查;唯有 DB 約束能在競態下保證唯一。
- 寫入用 `saveAndFlush`,catch `DataIntegrityViolationException` 轉成 409。

**5. 如何寫測試避免再發生?**
- **併發測試**:2 條 thread 搶同時段 → 斷言只成功 1 筆、被擋 1 筆、DB 只有 1 筆(本專案 `ReservationIntegrationTest`)。
- **Repository 測試**:覆蓋區間重疊各情境、status 過濾(本專案 `ReservationRepositoryTest` 情境 8–12)。

---

## 情境 2:Timeline API 很慢(回應 > 5 秒)

**1. 瓶頸在 API 邏輯還是 SQL?**
打開 `show-sql` 看實際送出的 SQL 數量與內容;用 log 計時切分「DB 時間 vs Java 時間」;對可疑 SQL 跑 `EXPLAIN ANALYZE`。

**2. 如何檢查 N+1?**
看 log 是否呈現「1 條主查詢 + N 條關聯查詢」——例如撈出 N 筆預約後,逐筆再查 user / room。Timeline 要顯示 username,最容易在組裝時逐筆觸發 lazy load。

**3. 建立哪些 index?**
`reservations(start_time)`(timeline 依日期區間查)、`(room_id, start_time)`(依房分組 / 排序)。

**4. 如何調整查詢?**
- 用 **JOIN FETCH** 一次把 user(及需要的 room)撈齊,消除 N+1。
- 以「啟用中房間清單」為主軸組裝,當天無預約的房給空 list(避免逐房再查)。
- `open-in-view: false` 強制在 Service 交易內完成載入,讓遺漏的 fetch 立即 fail-fast。

**5. 資料量持續成長如何重新設計?**
依 `start_time` 按月份做 range partition(partition pruning 只打單月)、讀查詢分流到讀庫、必要時改用投影只取需要欄位。

---

## 情境 3:`LazyInitializationException`

**1. 通常為什麼發生?**
在 persistence context(Hibernate Session)**已關閉後**才存取 LAZY 關聯。常見於:交易結束後才碰 lazy 欄位;或 `open-in-view: false` 時在 Controller / 序列化階段才觸發載入。

**2. 如何修正?**
在**交易內、查詢當下**就把需要的關聯撈齊——用 JOIN FETCH,或在交易內組成 DTO 後再回傳;不要把 entity 帶到交易外才碰它的 lazy 關聯。

**3. 選 fetch join / DTO projection / 調 transaction scope?**
- **固定形狀查詢** → JOIN FETCH(一次撈齊)。
- **只需部分欄位** → DTO projection(連 entity 都不載入)。
- **不靠放大 transaction scope**——那只是把 Session 開更久治標,反而掩蓋 N+1、佔用連線。

**4. 為什麼不建議全部改 EAGER?**
EAGER 會在**每次**載入該 entity 時無條件抓所有關聯 → 製造 N+1、抓一堆當下用不到的資料、且無法依場景調整。**LAZY + 按需 fetch** 才能由查詢決定要載什麼,效能可控。

---

## 附錄:本專案開發中實際遇到的問題

| 問題 | 原因 | 解法 |
|---|---|---|
| `created_at` / `updated_at` 為 null | 缺 `@EnableJpaAuditing`,auditing 未啟用 | 啟用(抽到 `JpaAuditingConfig`);注意 auditing 只在經 JPA 儲存時觸發,raw SQL 不會 |
| V6 初始資料觸發 exclusion constraint 衝突 | DB volume 沒清,新資料疊在殘留資料上 | `docker compose down -v` 後重跑;初始資料假設乾淨 DB |
| 建立預約回 500 看不出原因 | 多行貼上把 JSON body 弄壞 | 改單行 / `test.http`;**5xx 要看 server log 不是 response** |
| `@SpringBootTest` 報 `jpaAuditingHandler` 重複定義 | auditing bean 在測試 context 被註冊兩次 | 把 `allow-bean-definition-overriding` 只放在**測試** `application.properties`,正式環境保留保護 |
| Maven「Nothing to compile」改了沒生效 | 增量編譯沿用舊產物 | `./mvnw clean test` 強制重編 |
| JWT 角色不足應回 403 卻回 **401** | `accessDeniedHandler` 用 `sendError()` 會觸發 **error dispatch** 重進 filter chain;`JwtAuthenticationFilter` 是 `OncePerRequestFilter` 不會在 error dispatch 重跑 → `/error` 變匿名 → 誤判 401 | handler 改用 `response.setStatus()`(不觸發 error dispatch)。開 `org.springframework.security=DEBUG` 看到 log 出現 `Securing POST /error` + `anonymous` 才定位到 |
