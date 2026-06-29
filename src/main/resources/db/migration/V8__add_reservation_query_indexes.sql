-- 針對 reservations 的查詢建 btree index。
-- 注意:PostgreSQL 不會自動為 foreign key 建 index(只有 PK / UNIQUE 會),故 room_id、user_id 需手動建。
-- 衝突檢查(existsOverlapping)已由 V4 的 GiST exclusion index 服務,這裡不重複。

-- room 維度查詢:findByRoomIdWithDetails(WHERE room_id = ? ORDER BY start_time)。
-- 複合索引把等值欄位(room_id)放前、範圍/排序欄位(start_time)放後,可同時過濾並免去排序。
CREATE INDEX idx_reservations_room_id_start_time ON reservations (room_id, start_time);

-- 時間維度查詢:timeline / monthly-summary / top-used / overview 的日期區間過濾(只篩 start_time、不帶 room_id)。
CREATE INDEX idx_reservations_start_time ON reservations (start_time);

-- user 維度查詢:查某位使用者的所有預約(FK 無自動索引)。
CREATE INDEX idx_reservations_user_id ON reservations (user_id);
