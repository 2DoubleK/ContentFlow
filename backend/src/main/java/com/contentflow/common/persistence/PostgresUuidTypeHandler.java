package com.contentflow.common.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

@MappedTypes(UUID.class)
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, UUID value, JdbcType jdbcType) throws SQLException {
        PGobject uuid = new PGobject();
        uuid.setType("uuid");
        uuid.setValue(value.toString());
        statement.setObject(index, uuid);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toUuid(resultSet.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toUuid(resultSet.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return toUuid(statement.getObject(columnIndex));
    }

    private UUID toUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }
}
