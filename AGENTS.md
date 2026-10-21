# Project conventions

- Stack: Java 17+, Spring MVC 6, Thymeleaf, plain JDBC, PostgreSQL, WAR on Tomcat 10.1. Do not introduce Spring Boot or an ORM without a task requiring it.
- Production database is the user's Neon PostgreSQL `neondb`. Credentials are in the ignored local `.env` and Render environment variables. Do not print, commit, or bake credentials into images.
- Use PostgreSQL SQL syntax for all new schema/DAO work. Do not run schema changes, seed data, cleanup, or write tests against production as part of routine build verification.
- Put Java tests in `src/test/java` and test fixtures in `src/test/resources`. Production Docker builds copy only `src/main` and use `-Pproduction`, which skips both test compilation and execution. Keep tests and test dependencies out of the production WAR/image.
- GitHub Actions validates the production Docker container. Render builds from `Dockerfile` and deploys `main` only after CI checks pass. Keep CI path filters and `render.yaml` build filters aligned; tests/docs alone should not trigger builds.
- Use `.env.example` for placeholders only. Java does not automatically load `.env`; IDE/Tomcat and Render must receive environment variables.
