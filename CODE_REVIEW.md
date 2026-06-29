# CODE REVIEW

針對題目提供的範例程式,指出問題並提出改善方式。本專案的實作即依這些改善原則設計。

## 被審查的範例程式

```java
@PostMapping("/reserve")
public Reservation reserve(@RequestBody Map<String, String> body) {
    Long roomId = Long.valueOf(body.get("roomId"));
    Long userId = Long.valueOf(body.get("userId"));
    LocalDateTime start = LocalDateTime.parse(body.get("startTime"));
    LocalDateTime end = LocalDateTime.parse(body.get("endTime"));

    List<Reservation> reservations = reservationRepository.findAll();

    for (Reservation r : reservations) {
        if (r.getRoom().getId().equals(roomId)
                && r.getStartTime().equals(start)) {
            throw new RuntimeException("room booked");
        }
    }

    Room room = roomRepository.findById(roomId).get();
    User user = userRepository.findById(userId).get();

    Reservation reservation = new Reservation();
    reservation.setRoom(room);
    reservation.setUser(user);
    reservation.setStartTime(start);
    reservation.setEndTime(end);
    reservation.setStatus("approved");

    return reservationRepository.save(reservation);
}
```

## 問題清單(共 12 點)

| # | 問題 | 為什麼不好 | 改善(本專案做法) |
|---|---|---|---|
| 1 | **用 `Map<String,String>` 當 request body** | 無 schema、無型別、無法驗證、Swagger/IDE 無法辨識欄位 | 用 `CreateReservationRequest` record DTO(題目明文禁止用 Map) |
| 2 | **手動 `Long.valueOf` / `LocalDateTime.parse`** | 格式錯會丟 `NumberFormatException` / `DateTimeParseException`,未處理 → 500 | DTO 用正確型別,由框架自動綁定 |
| 3 | **完全沒有 Validation** | roomId/userId 可為 null、起訖時間、過去時間、容量、attendeeCount 都沒檢查 | Bean Validation(`@NotNull`、`@Min`…)+ Service 業務驗證 |
| 4 | **`Optional.get()` 不檢查存在** | `findById(...).get()` 查無資料丟 `NoSuchElementException` → 500,而非 404 | `.orElseThrow(() -> new ResourceNotFoundException(...))` |
| 5 | **`findAll()` 撈全表 + Java 迴圈比對** | 全表掃描隨資料量爆炸,且 `r.getRoom().getId()` 逐筆觸發 lazy load(N+1) | 把條件下推到 DB 查詢(`existsOverlapping`),走 index |
| 6 | **時間重疊判斷錯誤** | 只比 `startTime.equals(start)`,**漏掉真正的區間重疊**(10–11 與 10:30–11:30 不會被擋) | `existing.start < new.end AND existing.end > new.start` |
| 7 | **magic string `"approved"`** | 字串易打錯、無型別檢查;且新預約**直接設 approved,跳過 processing 審核** | enum + 受控建構子預設 `PROCESSING` |
| 8 | **直接回傳 Entity** | 暴露內部結構、lazy-loading 序列化問題、循環參照(題目明文禁止) | 回傳 `ReservationResponse` DTO |
| 9 | **沒有 `@Transactional`** | 檢查與寫入非同一交易,無原子性、無一致的 rollback 行為 | 寫入方法標 `@Transactional` |
| 10 | **併發不安全(TOCTOU race)** | 即使有 `findAll` 檢查,查詢到寫入之間仍可能被插入 → 兩筆都成功 | DB exclusion constraint 當最後防線(題目要求後端必須檢查) |
| 11 | **`throw new RuntimeException`** | 通用例外無法對應正確 HTTP 狀態(應 409),訊息粗糙 | 語意化例外(`ReservationConflictException` → 409)+ `@RestControllerAdvice` |
| 12 | **無授權 / security** | 任何人可用任意 userId 建立、且直接 approved,無權限控管 | Service 內授權(本人 / 角色);正式環境改 JWT |

## 重構後的樣貌

```java
@PostMapping("/api/reservations")
public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(request));
}
```

商業邏輯(存在性檢查、業務驗證、區間衝突檢查、寫入)收斂在 `@Transactional` 的 Service;衝突的最後防線交給 DB exclusion constraint;例外由全域處理器對應狀態碼。
