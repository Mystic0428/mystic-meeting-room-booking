-- 假資料:5 會議室、5 使用者、12 筆預約
-- 假設在乾淨的 DB 上跑(rooms/users 的 id 會是 1~5)

INSERT INTO rooms (name, capacity, floor, location, is_active) VALUES
  ('會議室 101', 6,  '1F', 'A 棟', true),
  ('會議室 102', 8,  '1F', 'A 棟', true),
  ('會議室 103', 15, '1F', 'A 棟', true),
  ('會議室 201', 20, '2F', 'B 棟', true),
  ('會議室 301', 30, '3F', 'B 棟', true);

INSERT INTO users (username, email, department, role) VALUES
  ('王小明', 'wang@example.com',  '研發部', 'USER'),
  ('李美麗', 'li@example.com',    '行銷部', 'USER'),
  ('陳大文', 'chen@example.com',  '人資部', 'REVIEWER'),
  ('林志明', 'lin@example.com',   '資訊部', 'ADMIN'),
  ('張雅婷', 'zhang@example.com', '財務部', 'USER');

-- 同一間房內時段不重疊;狀態刻意多樣,方便之後測 timeline / monthly-summary
INSERT INTO reservations (room_id, user_id, start_time, end_time, subject, purpose, attendee_count, status) VALUES
  (1, 1, '2026-07-01 09:00', '2026-07-01 10:00', 'Daily Standup',  '每日站會', 5,  'APPROVED'),
  (1, 2, '2026-07-01 10:00', '2026-07-01 11:00', 'Design Review',  '設計審查', 4,  'APPROVED'),
  (1, 3, '2026-07-02 14:00', '2026-07-02 15:30', 'Planning',       '規劃會議', 6,  'PROCESSING'),
  (2, 1, '2026-07-01 13:00', '2026-07-01 14:00', 'Marketing Sync', '行銷同步', 7,  'APPROVED'),
  (2, 4, '2026-07-03 09:30', '2026-07-03 11:00', 'Interview',      '面試',     3,  'APPROVED'),
  (2, 5, '2026-07-03 11:00', '2026-07-03 12:00', 'Budget',         '預算討論', 5,  'REJECTED'),
  (3, 2, '2026-07-01 10:00', '2026-07-01 12:00', 'Workshop',       '工作坊',   12, 'APPROVED'),
  (3, 3, '2026-07-04 15:00', '2026-07-04 16:00', 'Retro',          '回顧',     8,  'CANCEL_REQUESTED'),
  (3, 1, '2026-07-05 09:00', '2026-07-05 10:00', 'Demo',           '展示',     10, 'CANCELLED'),
  (4, 4, '2026-07-02 09:00', '2026-07-02 11:00', 'All Hands',      '全員大會', 18, 'APPROVED'),
  (4, 5, '2026-07-06 13:00', '2026-07-06 14:30', 'Training',       '教育訓練', 15, 'PROCESSING'),
  (5, 1, '2026-07-01 14:00', '2026-07-01 16:00', 'Town Hall',      '市民大會', 25, 'APPROVED');
