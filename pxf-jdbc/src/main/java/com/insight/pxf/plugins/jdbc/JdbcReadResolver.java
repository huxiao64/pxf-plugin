package com.insight.pxf.plugins.jdbc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hawq.pxf.api.*;
import org.apache.hawq.pxf.api.io.DataType;
import org.apache.hawq.pxf.api.utilities.ColumnDescriptor;
import org.apache.hawq.pxf.api.utilities.InputData;
import org.apache.hawq.pxf.api.utilities.Plugin;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class JdbcReadResolver extends Plugin implements ReadResolver {
    private static final Log LOG = LogFactory.getLog(JdbcReadResolver.class);
    //HAWQ数据库列定义-
    private ArrayList<ColumnDescriptor> columns = null;

    public JdbcReadResolver(InputData input) {
        super(input);
        columns = input.getTupleDescription();
    }

    @Override
    public List<OneField> getFields(OneRow row) throws Exception {
        //LOG.info("getFields");
        ResultSet result = (ResultSet) row.getData();
        LinkedList<OneField> fields = new LinkedList<OneField>();

        for (int i = 0; i < columns.size(); i++) {
            ColumnDescriptor column = columns.get(i);
            String colname = column.columnName();
            Object value = null;//result.getObject(column.columnName());

            OneField oneField = new OneField();
            oneField.type = column.columnTypeCode();

            switch (DataType.get(oneField.type)) {
                case INTEGER:
                    value = result.getInt(colname);
                    break;
                case FLOAT8:
                    value = result.getDouble(colname);
                    break;
                case REAL:
                    value = result.getFloat(colname);
                    break;
                case BIGINT:
                    value = result.getLong(colname);
                    break;
                case SMALLINT:
                    value = result.getShort(colname);
                    break;
                case BOOLEAN:
                    value = result.getBoolean(colname);
                    break;
                case BYTEA:
                    /*
                    byte[] bts = null;
                    if (val != null) {
                        int length = Array.getLength(val);
                        bts = new byte[length];
                        for (int j = 0; j < length; j++) {
                            bts[j] = Array.getByte(val, j);
                        }
                    }
                    gpdbOutput.setBytes(colIdx, bts);
                     */
                    value = result.getBytes(colname);
                    break;
                case VARCHAR:
                case BPCHAR:
                case CHAR:
                case TEXT:
                case NUMERIC:
                    value = result.getString(colname);
                    break;
                case TIMESTAMP:
                case DATE:
                    //value = result.getString(colname);
                    value = result.getDate(colname);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknwon Field Type : " + DataType.get(oneField.type).toString()
                            +", Column : " + column.toString());
            }
            //oneField.val = convertToJavaObject(oneField.type, column.columnTypeName(), value);
            oneField.val = value;
            fields.add(oneField);
        }
        return fields;
    }

}