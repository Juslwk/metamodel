/**
 * eobjects.org MetaModel
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */

package org.eobjects.metamodel.jdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.eobjects.metamodel.DataContext;
import org.eobjects.metamodel.UpdateCallback;
import org.eobjects.metamodel.UpdateScript;
import org.eobjects.metamodel.data.DataSet;
import org.eobjects.metamodel.query.OperatorType;
import org.eobjects.metamodel.query.Query;
import org.eobjects.metamodel.schema.Column;
import org.eobjects.metamodel.schema.ColumnType;
import org.eobjects.metamodel.schema.Schema;
import org.eobjects.metamodel.schema.Table;
import org.eobjects.metamodel.util.DateUtils;
import org.eobjects.metamodel.util.FileHelper;
import org.eobjects.metamodel.util.Month;

/**
 * Test case that tests SQLite interaction. The test uses an example database
 * from the trac project management system, src/test/resources/trac.db
 */
public class SqliteTest extends TestCase {

    private static final String CONNECTION_STRING = "jdbc:sqlite:";

    private Connection _connection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Class.forName("org.sqlite.JDBC");
        File sourceFile = new File("src/test/resources/trac.db");
        assert sourceFile.exists();

        File targetFile = new File("target/trac.db");
        FileHelper.copy(sourceFile, targetFile);

        _connection = DriverManager.getConnection(CONNECTION_STRING + targetFile.getAbsolutePath());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        _connection.close();
    }

    public void testDifferentOperators() throws Exception {
        JdbcTestTemplates.differentOperatorsTest(_connection);
    }

    public void testGetSchemas() throws Exception {
        DataContext dc = new JdbcDataContext(_connection);
        String[] schemaNames = dc.getSchemaNames();
        assertEquals("[null]", Arrays.toString(schemaNames));

        Schema schema = dc.getDefaultSchema();
        assertNotNull(schema);
        assertNull(schema.getName());

        Table[] tables = schema.getTables();
        assertEquals("[Table[name=system,type=TABLE,remarks=null], "
                + "Table[name=permission,type=TABLE,remarks=null], "
                + "Table[name=auth_cookie,type=TABLE,remarks=null], " + "Table[name=session,type=TABLE,remarks=null], "
                + "Table[name=session_attribute,type=TABLE,remarks=null], "
                + "Table[name=attachment,type=TABLE,remarks=null], " + "Table[name=wiki,type=TABLE,remarks=null], "
                + "Table[name=revision,type=TABLE,remarks=null], "
                + "Table[name=node_change,type=TABLE,remarks=null], " + "Table[name=ticket,type=TABLE,remarks=null], "
                + "Table[name=ticket_change,type=TABLE,remarks=null], "
                + "Table[name=ticket_custom,type=TABLE,remarks=null], " + "Table[name=enum,type=TABLE,remarks=null], "
                + "Table[name=component,type=TABLE,remarks=null], " + "Table[name=milestone,type=TABLE,remarks=null], "
                + "Table[name=version,type=TABLE,remarks=null], " + "Table[name=report,type=TABLE,remarks=null]]",
                Arrays.toString(tables));

        // Index- and key-info is not yet implemented in the JDBC driver

        assertEquals("[]", Arrays.toString(schema.getRelationships()));

        Table wikiTable = schema.getTableByName("WIKI");
        assertEquals(
                "[Column[name=name,columnNumber=0,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=version,columnNumber=1,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=2000000000], "
                        + "Column[name=time,columnNumber=2,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=2000000000], "
                        + "Column[name=author,columnNumber=3,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=ipnr,columnNumber=4,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=text,columnNumber=5,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=comment,columnNumber=6,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=readonly,columnNumber=7,type=INTEGER,nullable=true,nativeType=INTEGER,columnSize=2000000000]]",
                Arrays.toString(wikiTable.getColumns()));

        Table permissionTable = schema.getTableByName("PERMISSION");
        assertEquals(
                "[Column[name=username,columnNumber=0,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000], "
                        + "Column[name=action,columnNumber=1,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000]]",
                Arrays.toString(permissionTable.getColumns()));
    }

    public void testExecuteQuery() throws Exception {
        JdbcDataContext dc = new JdbcDataContext(_connection);
        Schema schema = dc.getDefaultSchema();
        assertEquals("Schema[name=null]", schema.toString());

        Table wikiTable = schema.getTableByName("WIKI");

        Query q = new Query().selectCount().from(wikiTable)
                .where(wikiTable.getColumnByName("name"), OperatorType.LIKE, "Trac%");
        assertEquals("SELECT COUNT(*) FROM wiki WHERE wiki.name LIKE 'Trac%'", q.toString());
        assertEquals(1, dc.getFetchSizeCalculator().getFetchSize(q));
        assertEquals(37, dc.executeQuery(q).toObjectArrays().get(0)[0]);

        Table permissionTable = schema.getTableByName("PERMISSION");
        Column typeColumn = permissionTable.getColumnByName("username");
        q = new Query().select(typeColumn).selectCount().from(permissionTable).groupBy(typeColumn).orderBy(typeColumn);
        assertEquals(
                "SELECT permission.username, COUNT(*) FROM permission GROUP BY permission.username ORDER BY permission.username ASC",
                q.toString());

        List<Object[]> data = dc.executeQuery(q).toObjectArrays();
        assertEquals(2, data.size());
        assertEquals("[anonymous, 12]", Arrays.toString(data.get(0)));
        assertEquals("[authenticated, 4]", Arrays.toString(data.get(1)));
    }

    public void testQualifiedLabel() throws Exception {
        DataContext dc = new JdbcDataContext(_connection);
        Schema schema = dc.getDefaultSchema();
        Table wikiTable = schema.getTableByName("WIKI");
        assertEquals("wiki", wikiTable.getQualifiedLabel());
        Column nameColumn = wikiTable.getColumnByName("name");
        assertEquals("wiki.name", nameColumn.getQualifiedLabel());

        assertEquals(
                "Column[name=name,columnNumber=0,type=VARCHAR,nullable=true,nativeType=TEXT,columnSize=2000000000]", dc
                        .getColumnByQualifiedLabel("wiki.name").toString());
        assertEquals("Table[name=wiki,type=TABLE,remarks=null]", dc.getTableByQualifiedLabel("WIKI").toString());
    }

    public void testSingleQuoteInQuery() throws Exception {
        JdbcDataContext dc = new JdbcDataContext(_connection);
        Schema schema = dc.getDefaultSchema();
        Table wikiTable = schema.getTableByName("WIKI");
        Column nameColumn = wikiTable.getColumnByName("name");

        Query q = dc.query().from(wikiTable).select(nameColumn).where(nameColumn).isEquals("m'jello").toQuery();
        assertEquals(16384, dc.getFetchSizeCalculator().getFetchSize(q));

        assertEquals("SELECT wiki.name FROM wiki WHERE wiki.name = 'm'jello'", q.toSql());
        assertEquals("SELECT wiki.name FROM wiki WHERE wiki.name = 'm''jello'", dc.getQueryRewriter().rewriteQuery(q));

        DataSet ds = dc.executeQuery(q);
        assertFalse(ds.next());
    }

    public void testWorkingWithDates() throws Exception {
        JdbcDataContext dc = new JdbcDataContext(_connection);
        final Schema schema = dc.getDefaultSchema();
        JdbcTestTemplates.createInsertAndUpdateDateTypes(dc, schema, "test_table");
    }

    public void testCharOfSizeOne() throws Exception {
        JdbcTestTemplates.meaningOfOneSizeChar(_connection);
    }

    public void testAutomaticConversionWhenInsertingString() throws Exception {
        Connection connection = _connection;

        assertNotNull(connection);

        try {
            // clean up, if nescesary
            connection.createStatement().execute("DROP TABLE test_table");
        } catch (SQLException e) {
            // do nothing
        }

        assertFalse(connection.isReadOnly());

        JdbcDataContext dc = new JdbcDataContext(connection);
        final Schema schema = dc.getDefaultSchema();

        dc.executeUpdate(new UpdateScript() {
            @Override
            public void run(UpdateCallback cb) {
                Table table = cb.createTable(schema, "test_table").withColumn("id").ofType(ColumnType.INTEGER)
                        .asPrimaryKey().withColumn("birthdate").ofType(ColumnType.DATE).execute();

                cb.insertInto(table).value("id", "1").execute();
                cb.insertInto(table).value("id", 2).value("birthdate", "2011-12-21").execute();
                cb.insertInto(table).value("id", 3).value("birthdate", DateUtils.get(2011, Month.DECEMBER, 22))
                        .execute();
            }
        });

        dc.refreshSchemas();

        Column idColumn = dc.getColumnByQualifiedLabel("test_table.id");
        assertTrue(idColumn.isPrimaryKey());

        Column column = dc.getColumnByQualifiedLabel("test_table.birthdate");
        assertFalse(column.isPrimaryKey());

        // the jdbc driver represents the date as a VARCHAR
        assertEquals("Column[name=birthdate,columnNumber=1,type=VARCHAR,nullable=true,"
                + "nativeType=DATE,columnSize=2000000000]", column.toString());

        DataSet ds = dc.query().from("test_table").select("id").and("birthdate").execute();
        assertTrue(ds.next());
        assertEquals("Row[values=[1, null]]", ds.getRow().toString());
        assertEquals("java.lang.Integer", ds.getRow().getValue(0).getClass().getName());
        assertTrue(ds.next());
        assertEquals("Row[values=[2, 2011-12-21]]", ds.getRow().toString());
        assertEquals("java.lang.String", ds.getRow().getValue(1).getClass().getName());
        assertTrue(ds.next());
        assertEquals("Row[values=[3, 2011-12-22]]", ds.getRow().toString());
        assertEquals("java.lang.String", ds.getRow().getValue(1).getClass().getName());
        assertFalse(ds.next());
        ds.close();

        connection.createStatement().execute("DROP TABLE test_table");
    }

    public void testConvertClobToString() throws Exception {
        JdbcDataContext dc = new JdbcDataContext(_connection);
        JdbcTestTemplates.convertClobToString(dc);
    }
    
    
    public void testInterpretationOfNull() throws Exception {
        JdbcTestTemplates.interpretationOfNulls(_connection);
    }
}