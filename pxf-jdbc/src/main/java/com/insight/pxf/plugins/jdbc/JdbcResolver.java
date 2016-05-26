package com.insight.pxf.plugins.jdbc;

import org.apache.hawq.pxf.api.OneField;
import org.apache.hawq.pxf.api.OneRow;
import org.apache.hawq.pxf.api.WriteResolver;
import org.apache.hawq.pxf.api.io.DataType;
import org.apache.hawq.pxf.api.utilities.InputData;
import org.apache.hawq.pxf.api.utilities.Plugin;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class JdbcResolver extends Plugin implements WriteResolver {

    private static final Logger LOG = Logger.getLogger(JdbcResolver.class);
    private StringBuilder bldr = new StringBuilder();
    private OneRow row = new OneRow();
    private final String INSERT_INTO = "INSERT INTO ";
    private final String TABLE_NAME;
    private final String VALUES = " VALUES (";
    private final String COMMA = ",";
    private final String TICK = "'";
    private final String CLOSE_STMT = ");";

    public JdbcResolver(InputData input) {
        super(input);
        TABLE_NAME = input.getUserProperty("TABLE_NAME");
    }

    @Override
    public OneRow setFields(List<OneField> records) throws Exception {
        LOG.info("SETFIELDS");
        bldr.setLength(0);

        bldr.append(INSERT_INTO);
        bldr.append(TABLE_NAME);
        bldr.append(VALUES);

        int i = 0;
        int length = records.size();
        for (OneField f : records) {
            switch (DataType.get(f.type)) {
                case BIGINT:
                case BOOLEAN:
                case BPCHAR:
                case BYTEA:
                case DATE:
                case FLOAT8:
                case INTEGER:
                case NUMERIC:
                case REAL:
                case SMALLINT:
                case TIME:
                case TIMESTAMP:
                    bldr.append(f.val.toString());
                    break;
                case CHAR:
                case TEXT:
                case VARCHAR:
                    bldr.append(TICK);
                    bldr.append(f.val.toString().replaceAll("'", "''"));
                    bldr.append(TICK);
                    break;
                case UNSUPPORTED_TYPE:
                default:
                    throw new IOException("Unsupported type " + f.type);
            }

            if (++i < length) {
                bldr.append(COMMA);
            }
        }

        bldr.append(CLOSE_STMT);

        row.setData(bldr.toString());
        LOG.info("STATEMENT: " + bldr.toString());
        return row;
    }

}