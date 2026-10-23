-- Metadata hiển thị không thay đổi public ID hoặc nội dung file trên provider.
ALTER TABLE media_assets ADD COLUMN title VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE media_assets ADD COLUMN alt_text VARCHAR(300) NOT NULL DEFAULT '';
