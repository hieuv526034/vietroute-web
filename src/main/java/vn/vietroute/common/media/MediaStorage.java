package vn.vietroute.common.media;

/** Adapter lưu ảnh công khai; module nghiệp vụ không phụ thuộc SDK hoặc credentials Cloudinary. */
public interface MediaStorage {
    StoredImage upload(String publicId, byte[] bytes);

    /** Xóa lặp an toàn: ảnh không còn trên nhà cung cấp cũng được xem là đã xóa. */
    void delete(String publicId);

    record StoredImage(String url, String thumbnailUrl, String format, long bytes, int width, int height) {}
}
