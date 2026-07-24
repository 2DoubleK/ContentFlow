package com.contentflow.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

class PostgresUuidTypeHandlerTest {
    @Test
    void bindsUuidAsPostgresUuidObject() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        UUID value = UUID.randomUUID();

        new PostgresUuidTypeHandler().setNonNullParameter(statement, 1, value, null);

        ArgumentCaptor<PGobject> captured = ArgumentCaptor.forClass(PGobject.class);
        verify(statement).setObject(org.mockito.ArgumentMatchers.eq(1), captured.capture());
        assertThat(captured.getValue().getType()).isEqualTo("uuid");
        assertThat(captured.getValue().getValue()).isEqualTo(value.toString());
    }
}
