package io.kestra.repository.h2;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class H2Repository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {
    public H2Repository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @SneakyThrows
    public void persist(T entity, DSLContext context, @Nullable Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        int affectedRows = context
            .update(table)
            .set(finalFields)
            .where(AbstractJdbcRepository.field("key").eq(key(entity)))
            .execute();

        if (affectedRows == 0) {
            context
                .insertInto(table)
                .set(AbstractJdbcRepository.field("key"), key(entity))
                .set(finalFields)
                .execute();
        }
    }

    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        if (fields.size() > 1) {
            throw new IllegalStateException("Too many fields for h2 '" + fields + "'");
        }

        Field<Object> field = AbstractJdbcRepository.field(fields.get(0));

        List<LikeEscapeStep> match = Arrays
            .stream(query.split("\\p{P}|\\p{S}|\\p{Z}"))
            .map(s -> field.likeIgnoreCase("%" + s.toUpperCase(Locale.ROOT) + "%"))
            .collect(Collectors.toList());

        if (match.size() == 0) {
            return DSL.falseCondition();
        }

        return DSL.and(match);
    }

    @SuppressWarnings("unchecked")
    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        Result<Record> results = this.limit(
                context.select(DSL.asterisk(), DSL.count().over().as("total_count"))
                    .from(this
                        .sort(select, pageable)
                        .asTable("page")
                    )
                    .where(DSL.trueCondition()),
                pageable
            )
            .fetch();

        Integer totalCount = results.size() > 0 ? results.get(0).get("total_count", Integer.class) : 0;

        List<E> map = results
            .map((Record record) -> mapper.map((R) record));

        return new ArrayListTotal<>(map, totalCount);
    }
}
