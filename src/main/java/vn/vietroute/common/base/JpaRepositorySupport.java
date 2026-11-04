package vn.vietroute.common.base;

import jakarta.persistence.EntityManager;
import java.io.Serializable;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

/** CRUD Spring Data JPA dùng transaction của caller; không có SQL nghiệp vụ trong lớp nền. */
public abstract class JpaRepositorySupport<T, K extends Serializable> extends SimpleJpaRepository<T, K> {

    protected final EntityManager entityManager;

    protected JpaRepositorySupport(Class<T> model, EntityManager manager) {
        super(model, manager);
        this.entityManager = manager;
    }

    /** Đọc lại version/audit sau SQL atomic trên chính transaction JPA đang mở. */
    public void refresh(T value) {
        entityManager.refresh(value);
    }
}
