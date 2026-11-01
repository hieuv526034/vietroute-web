package vn.vietroute.common.base;

import jakarta.persistence.EntityManager;
import java.io.Serializable;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** CRUD Spring Data JPA và callback JDBC dùng cùng transaction; không có SQL nghiệp vụ trong lớp nền. */
public abstract class JpaRepositorySupport<T, K extends Serializable> extends SimpleJpaRepository<T, K> {

    protected final EntityManager entityManager;

    protected final JdbcTransactions transactions;

    protected JpaRepositorySupport(Class<T> model, EntityManager manager, JdbcTransactions transactions) {
        super(model, manager);
        this.entityManager = manager;
        this.transactions = transactions;
    }

    protected <R> R read(SqlWork<R> work) {
        return transactions.execute(work);
    }

    /** Đọc lại version/audit sau SQL atomic trên chính transaction JPA đang mở. */
    public void refresh(T value) {
        entityManager.refresh(value);
    }
}
