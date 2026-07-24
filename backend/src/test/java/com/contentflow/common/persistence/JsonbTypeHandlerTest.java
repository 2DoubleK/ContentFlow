package com.contentflow.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class JsonbTypeHandlerTest {
    @Test
    void bindsJsonTextAsPostgresJsonb() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        new JsonbTypeHandler().setNonNullParameter(statement, 1, "[]", null);

        var captor = org.mockito.ArgumentCaptor.forClass(PGobject.class);
        verify(statement).setObject(org.mockito.ArgumentMatchers.eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("jsonb");
        assertThat(captor.getValue().getValue()).isEqualTo("[]");
    }
}
