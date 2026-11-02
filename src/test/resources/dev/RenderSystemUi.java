/** AUTHDB04: CLI tương thích cho nhóm regression đã chuyển vào JUnit; không còn helper action riêng. */
public final class RenderSystemUi {

    private RenderSystemUi() {}

    public static void main(String[] args) throws Exception {
        int status = new ProcessBuilder("python3", ".github/scripts/assert-auth-regressions.py", "foundation")
            .inheritIO()
            .start()
            .waitFor();
        if (status != 0) throw new IllegalStateException("Regression chưa đạt; xem Maven verify với PostgreSQL cô lập");
    }
}
