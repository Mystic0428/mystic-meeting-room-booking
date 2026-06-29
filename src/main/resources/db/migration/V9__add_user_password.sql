-- 加分項 JWT 登入用:users 加密碼欄位(存 BCrypt 雜湊,不存明文)。
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- 既有種子使用者給一組共用 demo 密碼(明文 = password123),方便登入測試。
-- 雜湊由 BCrypt(strength 10)產生。
UPDATE users SET password_hash = '$2a$10$gCSWX8kKwwhd96nqjm6q/O/.J6OeZpuMjmezZDSlmfjUtsk8bWTa2';
