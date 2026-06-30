-- 以涵蓋索引 (start_time, status) 取代 V8 的單欄 (start_time)。
-- 理由(已用 EXPLAIN ANALYZE 在 ~20 萬筆資料上驗證):
--   * monthly-summary 的「WHERE start_time 範圍 + GROUP BY status」可走 Index Only Scan
--     (start_time、status 皆在索引內,Heap Fetches: 0),不必回表。
--   * 最左前綴 (start_time) 仍服務 timeline / overview 等只以 start_time 範圍過濾的查詢,
--     故原本的單欄 start_time 索引被涵蓋,可安全移除以省一份寫入成本。
CREATE INDEX idx_reservations_start_time_status ON reservations (start_time, status);

DROP INDEX idx_reservations_start_time;

-- 刻意「不」建 (status, start_time):EXPLAIN ANALYZE 顯示單日 timeline 查詢
-- (status = ? 且 start_time 落在某一天)範圍已足夠窄,planner 仍選用 start_time 索引、
-- 不採用 (status, start_time);加上 status 僅 5 種值、單欄選擇性低。為避免盲目加索引,故不建。
