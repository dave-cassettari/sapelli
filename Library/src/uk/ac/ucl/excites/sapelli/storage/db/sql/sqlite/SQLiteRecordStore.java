/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import uk.ac.ucl.excites.sapelli.shared.db.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.BitInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.shared.util.TransactionalStringBuilder;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteBlobColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteBooleanColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteDoubleColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteIntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteStringColumn;
import uk.ac.ucl.excites.sapelli.storage.model.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Index;
import uk.ac.ucl.excites.sapelli.storage.model.ListColumn;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.BooleanColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.ByteArrayColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.FloatColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.TimeStampColumn;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStamp;

/**
 * @author mstevens
 *
 */
public abstract class SQLiteRecordStore extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>
{
	
	// Statics----------------------------------------------
	static public final String DATABASE_FILE_EXTENSION = "sqlite3";
	static public final String BACKUP_SUFFIX = "_Backup";
	static public final String PARAM_PLACEHOLDER = "?";
	
	/**
	 * @see http://catalogue.pearsoned.co.uk/samplechapter/067232685X.pdf
	 */
	static public final Pattern identifierPattern = Pattern.compile("[a-zA-Z_]+[0-9a-zA-Z_]*");
	
	/**
	 * Test method
	 */
	/*static public void testIdentifierPattern()
	{
		String[] identifiersToTest =
				new String[] {	"mytable", "my_field", "xyz123", "_table14343", "_1col", "a", // all valid
								"my table", "my-field", "my.table", "123xyz", "_abc?col", "2" }; // all invalid
		for(String identifier : identifiersToTest)
			System.out.println(identifier + "\t" + identifierPattern.matcher(identifier).matches());
	}*/
	
	// Dynamics---------------------------------------------
	private final SQLiteTableFactory factory;
	
	public SQLiteRecordStore(StorageClient client) throws Exception
	{
		super(client, PARAM_PLACEHOLDER);
		factory = new SQLiteTableFactory();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#getTableFactory()
	 */
	@Override
	protected TableFactory getTableFactory()
	{
		return factory;
	}

	/**
	 * In SQlite basic transactions (those controlled with BEGIN...COMMIT/ROLLBACK) cannot
	 * be nested (for that one needs to use the SAVEPOINT and RELEASE commands, which we won't
	 * use here). However, for flexibility reasons we will pretend that it is possible (i.e. we
	 * don't throw an exception if a request arrives to open a 2nd, 3rd, etc. transaction).
	 * 
	 * @see <a href="http://sqlite.org/lang_transaction.html">http://sqlite.org/lang_transaction.html</a>
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doStartTransaction()
	 */
	@Override
	protected void doStartTransaction() throws DBException
	{
		if(!isInTransaction()) // TODO re-assess this for android/java
			try
			{
				executeSQL("BEGIN TRANSACTION;");
			}
			catch(Exception ex)
			{
				throw new DBException("Could not open SQLite transaction", ex);
			}
	}

	@Override
	protected void doCommitTransaction() throws DBException
	{
		if(getOpenTransactions() == 1) // higher numbers indicate nested transactions which are simulated
			try
			{
				executeSQL("COMMIT TRANSACTION;");
			}
			catch(Exception ex)
			{
				throw new DBException("Could not commit SQLite transaction", ex);
			}
	}

	@Override
	protected void doRollbackTransaction() throws DBException
	{
		if(getOpenTransactions() == 1) // higher numbers indicate nested transactions which are simulated
			try
			{
				executeSQL("ROLLBACK TRANSACTION;");
			}
			catch(Exception ex)
			{
				throw new DBException("Could not roll-back SQLite transaction", ex);
			}
	}
	
	/**
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#sanitiseIdentifier(java.lang.String)
	 * @see http://catalogue.pearsoned.co.uk/samplechapter/067232685X.pdf
	 */
	@Override
	protected String sanitiseIdentifier(String identifier)
	{
		if(identifierPattern.matcher(identifier).matches())
			return identifier;
		else
			return '[' + identifier + ']';
	}

	@Override
	protected boolean doesTableExist(String tableName)
	{
		ISQLiteCursor cursor = null;
		try
		{
			cursor = executeQuery(	"SELECT name FROM sqlite_master WHERE type='table' AND name=?;",
									Collections.<SQLiteColumn<?, ?>> singletonList(new SQLiteStringColumn<String>(this, "name", null, null, null, null)),
									Collections.<Object> singletonList(tableName));
			return cursor != null && cursor.hasRow();
		}
		catch(DBException e)
		{
			e.printStackTrace(System.err);
			return false;
		}
		finally
		{
			if(cursor != null)
				cursor.close();
		}
	}
	
	/**
	 * @param sql
	 * @param paramCols list of SQLiteColumns which the parameters (?s) in the sql correspond to
	 * @param sapArguments list of SapType object which are the values to be bound to the parameters
	 * @return an cursor to iterate over the results
	 * @throws DBException
	 */
	protected abstract ISQLiteCursor executeQuery(String sql, List<SQLiteColumn<?, ?>> paramCols, List<Object> sapArguments) throws DBException;

	@Override
	protected void doBackup(File destinationFolder) throws DBException
	{
		File currentDB = getDatabaseFile();
		String extension = FileHelpers.getFileExtension(currentDB);
		File backupDB = new File(destinationFolder, FileHelpers.trimFileExtensionAndDot(currentDB.getName()) + BACKUP_SUFFIX + "_" + TimeUtils.getTimestampForFileName() + "." + (extension.isEmpty() ? DATABASE_FILE_EXTENSION : extension));
		if(currentDB != null && currentDB.exists() && destinationFolder.canWrite())
		{	// File copy:
			try
			{
				FileUtils.copyFile(currentDB, backupDB);
			}
			catch(IOException e)
			{
				throw new DBException("Failed to back-up SQLite database to: " + backupDB.getAbsolutePath(), e);
			}
		}
		else
			throw new DBException("Failed to back-up SQLite database");
	}
	
	protected abstract File getDatabaseFile();
	
	/**
	 * @param sql
	 * @param paramCols - may be null
	 * @return
	 * @throws DBException
	 */
	protected abstract SapelliSQLiteStatement getStatement(String sql, List<SQLiteColumn<?, ?>> paramCols) throws DBException;
	
	/**
	 * @author mstevens
	 *
	 */
	public class SQLiteTable extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>.SQLTable
	{

		private SapelliSQLiteStatement existsStatement;
		private SapelliSQLiteStatement insertStatement;
		private SapelliSQLiteStatement updateStatement;
		private SapelliSQLiteStatement deleteStatement;
		private SapelliSQLiteStatement countStatement;

		public SQLiteTable(Schema schema)
		{
			super(schema);
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#isRecordInDB(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public boolean isRecordInDB(Record record) throws DBException
		{
			if(autoIncrementKeyColumn != null)
				return autoIncrementKeyColumn.isValueSet(record);
			else
			{
				if(existsStatement == null)
				{
					SelectROWIDHelper selectROWIDHelper = new SelectROWIDHelper(this);
					existsStatement = getStatement(selectROWIDHelper.getQuery(), selectROWIDHelper.getParameterColumns());
				}
				else
					existsStatement.clearAllBindings();
				
				// Bind parameters:
				existsStatement.retrieveAndBindAll(record);
				
				// Execute:
				return existsStatement.executeLongQuery() != null;
			}
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#insert(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public void insert(Record record) throws DBException
		{
			if(insertStatement == null)
			{
				RecordInsertHelper insertHelper = new RecordInsertHelper(this);
				insertStatement = getStatement(insertHelper.getQuery(), insertHelper.getParameterColumns());
			}
			else
				insertStatement.clearAllBindings(); // clear bindings for reuse

			// Bind parameters:
			insertStatement.retrieveAndBindAll(record);
			
			// Execute:
			long rowID = insertStatement.executeInsert();
			
			// Set auto-incrementing key value:
			if(autoIncrementKeyColumn != null)
				autoIncrementKeyColumn.storeValue(record, rowID);
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#update(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public void update(Record record) throws DBException
		{
			if(updateStatement == null)
			{
				RecordUpdateHelper updateHelper = new RecordUpdateHelper(this);
				updateStatement = getStatement(updateHelper.getQuery(), updateHelper.getParameterColumns());
			}
			else
				updateStatement.clearAllBindings(); // clear bindings for reuse

			// Bind parameters:
			updateStatement.retrieveAndBindAll(record);
			
			// Execute:
			updateStatement.executeUpdate();
		}
		
		public void upsert(Record record) throws DBException
		{
			// TODO first read http://stackoverflow.com/questions/3634984/insert-if-not-exists-else-update 
			// and http://stackoverflow.com/questions/418898/sqlite-upsert-not-insert-or-replace
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#delete(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public void delete(Record record) throws DBException
		{
			if(deleteStatement == null)
			{
				RecordDeleteHelper deleteHelper = new RecordDeleteHelper(this);
				deleteStatement = getStatement(deleteHelper.getQuery(), deleteHelper.getParameterColumns());
			}
			else
				deleteStatement.clearAllBindings(); // clear bindings for reuse

			// Bind parameters:
			deleteStatement.retrieveAndBindAll(record);
			
			// Execute:
			deleteStatement.executeDelete();
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#executeRecordSelection(uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.RecordSelectHelper)
		 */
		@Override
		protected List<Record> executeRecordSelection(RecordSelectHelper selection) throws DBException
		{
			ISQLiteCursor cursor = null;
			
			try
			{
				// Execute query (also binds parameters) to get cursor:
				cursor = executeQuery(selection.getQuery(), selection.getParameterColumns(), selection.getSapArguments());
				// Deal with cursor:
				if(cursor == null || !cursor.hasRow())
					// No results:
					return Collections.<Record> emptyList();
				else
				{	// Process cursor rows and create corresponding records:
					List<Record> result = new ArrayList<Record>();
					while(cursor.moveToNext())
					{
						Record record = schema.createRecord();
						int i = 0;
						for(SQLiteColumn<?, ?> sqliteCol : sqlColumns.values())
							sqliteCol.store(record, cursor, i++);
						result.add(record);
					}
					return result;
				}
			}
			finally
			{
				if(cursor != null)
					cursor.close(); // !!!
			}
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#getRecordCount()
		 */
		@Override
		public long getRecordCount() throws DBException
		{
			if(countStatement == null)
				countStatement = getStatement(new RecordCountHelper(this).getQuery(), null);
			return countStatement.executeLongQuery();
		}
		
		@Override
		public void drop() throws DBException
		{
			// Release resources:
			if(existsStatement != null)
				existsStatement.close();
			if(insertStatement != null)
				insertStatement.close();
			if(updateStatement != null)
				updateStatement.close();
			if(deleteStatement != null)
				deleteStatement.close();
			if(countStatement != null)
				countStatement.close();
			
			// Drop table:
			super.drop();
		}

	}
	
	/**
	 * @author mstevens
	 *
	 * @param <SQLType>
	 * @param <SapType>
	 */
	public abstract class SQLiteColumn<SQLType, SapType> extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>.SQLColumn<SQLType, SapType>
	{

		/**
		 * @param type
		 * @param constraint
		 * @param sourceSchema
		 * @param sourceColumn
		 * @param mapping - may be null in case SQLType = SapType
		 */
		public SQLiteColumn(String type, String constraint, Schema sourceSchema, Column<SapType> sourceColumn, TypeMapping<SQLType, SapType> mapping)
		{
			super(type, constraint, sourceSchema, sourceColumn, mapping);
		}

		/**
		 * @param name
		 * @param type
		 * @param constraint
		 * @param sourceSchema
		 * @param sourceColumn
		 * @param mapping - may be null in case SQLType = SapType
		 */
		public SQLiteColumn(String name, String type, String constraint, Schema sourceSchema, Column<SapType> sourceColumn, TypeMapping<SQLType, SapType> mapping)
		{
			super(name, type, constraint, sourceSchema, sourceColumn, mapping);
		}
		/**
		 * @param statement
		 * @param paramIdx
		 * @param record
		 * @throws DBException
		 */
		public void retrieveAndBind(SapelliSQLiteStatement statement, int paramIdx, Record record) throws DBException
		{
			bind(statement, paramIdx, retrieve(record));
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param sapValue
		 * @throws DBException
		 */
		@SuppressWarnings("unchecked")
		public void bindSapelliObject(SapelliSQLiteStatement statement, int paramIdx, Object sapValue) throws DBException
		{
			bind(statement, paramIdx, sapValue != null ? mapping.toSQLType((SapType) sapValue) : null);
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param value
		 * @throws DBException
		 */
		public void bind(SapelliSQLiteStatement statement, int paramIdx, SQLType value) throws DBException
		{
			if(value != null)
				bindNonNull(statement, paramIdx, value);
			else
				statement.bindNull(paramIdx);
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param value
		 * @throws DBException
		 */
		protected abstract void bindNonNull(SapelliSQLiteStatement statement, int paramIdx, SQLType value) throws DBException;

		@Override
		protected String getNullString()
		{
			return "null";
		}

		@Override
		protected String getQuoteChar()
		{
			return "'";
		}

		@Override
		protected String getQuoteEscape()
		{
			return "''";
		}
		
		/**
		 * @param record
		 * @param cursor
		 * @param columnIdx
		 * @throws DBException
		 */
		public void store(Record record, ISQLiteCursor cursor, int columnIdx) throws DBException
		{
			store(record, getValueOrNull(cursor, columnIdx));
		}
		
		/**
		 * @param cursor
		 * @param columnIdx
		 * @return
		 * @throws DBException
		 */
		public SQLType getValueOrNull(ISQLiteCursor cursor, int columnIdx) throws DBException
		{
			if(cursor.isNull(columnIdx))
				return null;
			return getValue(cursor, columnIdx);
		}
		
		/**
		 * @param cursor
		 * @param columnIdx
		 * @return
		 * @throws DBException
		 */
		protected abstract SQLType getValue(ISQLiteCursor cursor, int columnIdx) throws DBException;

	}
	
	/**
	 * @author mstevens
	 *
	 * @see http://www.sqlite.org/lang_createtable.html
	 */
	protected class SQLiteTableFactory extends BasicTableFactory
	{
		
		@Override
		protected void initialiseTable() throws DBException
		{
			table = new SQLiteTable(schema);
		}
		
		private String getColumnConstraint(Column<?> sourceColum)
		{
			TransactionalStringBuilder bldr = new TransactionalStringBuilder(SPACE);
		
			// Primary key & unique indexes:
			Iterator<Index> idxIter = indexesToProcess.iterator();
			while(idxIter.hasNext()) // we use an iterator instead of a for-each loop to allow save remove of items during iteration
			{
				Index idx = idxIter.next();
				if(idx.containsColumn(sourceColum, false) && !idx.isMultiColumn())
				{	// the sourceColumn is indexed (on its own) ...
					bldr.openTransaction();
					// Check which kind of index it is:
					if(idx == schema.getPrimaryKey())
					{	// the sourceColumn is the primary key
						bldr.append("PRIMARY KEY");
						// ASC/DESC?
						// conflict-clause?
						if(idx instanceof AutoIncrementingPrimaryKey)
							bldr.append("AUTOINCREMENT");
					}
					else if(idx.isUnique())
					{	//Regular single-column, unique index (unnamed)
						bldr.append("UNIQUE");
						// conflict-clause?
					}
					// else : index will have to be created separately (outside of column & table definition)
					
					if(!bldr.isCurrentTransactionEmpty())
					{
						bldr.commitTransaction();
						// We are done processing this index:
						idxIter.remove();
					}
					else
						bldr.rollbackTransaction();
				}
			}
	
			// Optionality:
			boolean optional = sourceColum.isOptional();
			// Check parents, if one of them is optional the subcolumn in the DB should be too:
			if(!optional)
				for(Column<?> parent : parents) // iterates through the stack from bottom to top! (But that's fine in this case)
					if(parent.isOptional())
					{
						optional = true;
						break;
					}
			if(!optional)
				bldr.append("NOT NULL");
			// TODO Reassess this: it could be a bad idea because it means we cannot persistently store incomplete records (which was no problem with DB4O).
			
			// TODO Default value?
			
			// foreign-key-clause?
			/*if(sourceColum instanceof ForeignKeyColumn)
			{
				// ...
			}*/
			
			return bldr.toString();
		}
		
		protected List<String> getTableConstraints()
		{
			List<String> tConstraints = new ArrayList<String>();
			
			// Primary key & unique indexes:
			Iterator<Index> idxIter = indexesToProcess.iterator();
			while(idxIter.hasNext()) // we use an iterator instead of a for-each loop to allow save remove of items during iteration
			{
				Index idx = idxIter.next();
				TransactionalStringBuilder bldr = new TransactionalStringBuilder(SPACE);
				// Check which kind of index it is:
				if(idx == schema.getPrimaryKey())
					// index is the primary key:
					bldr.append("PRIMARY KEY");
				else if(idx.isUnique())
					// index is a regular, unique index:
					bldr.append("UNIQUE");
				else
					// index is a not the primary key, nor unique, these cannot be created as part of the SQLite table definition
					continue; // will be created separately from the table
				
				// Continue generating constraint definition for primary key or unique index:
				bldr.append("(");
				bldr.openTransaction(", ");
				// List indexed columns:
				for(Column<?> idxCol : idx.getColumns(false))
					// idxCol may be a composite (like a ForeignKeyColumn), so loop over each SQLiteColumn that represents part of it:
					for(SQLiteColumn<?, ?> idxSCol : table.getSQLColumns(idxCol))
						bldr.append(idxSCol.name);
				bldr.commitTransaction(false);
				bldr.append(")", false);
				// conflict-clause?
				
				// Add constraint:
				tConstraints.add(bldr.toString());
				
				// We are done processing this index:
				idxIter.remove();
			}
			
			// foreign-key-clause?
			
			return tConstraints;
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.visitors.ColumnVisitor#visit(uk.ac.ucl.excites.sapelli.storage.model.columns.BooleanColumn)
		 * @see Section 1.1 in http://www.sqlite.org/datatype3.html
		 */
		@Override
		public void visit(BooleanColumn boolCol)
		{
			table.addColumn(new SQLiteBooleanColumn(SQLiteRecordStore.this, getColumnConstraint(boolCol), schema, boolCol));
		}
		
		@Override
		public void visit(final TimeStampColumn timeStampCol)
		{
			table.addColumn(new SQLiteStringColumn<TimeStamp>(SQLiteRecordStore.this, getColumnConstraint(timeStampCol), schema, timeStampCol, new TypeMapping<String, TimeStamp>()
			{

				@Override
				public String toSQLType(TimeStamp value)
				{
					return timeStampCol.toString(value);
				}

				@Override
				public TimeStamp toSapelliType(String value)
				{
					return timeStampCol.parse(value);
				}
				
			}));
		}
		
		@Override
		public void visit(ByteArrayColumn byteArrayCol)
		{
			table.addColumn(new SQLiteBlobColumn<byte[]>(SQLiteRecordStore.this, getColumnConstraint(byteArrayCol), schema, byteArrayCol, null));
		}
		
		@Override
		public void visit(StringColumn stringCol)
		{
			table.addColumn(new SQLiteStringColumn<String>(SQLiteRecordStore.this, getColumnConstraint(stringCol), schema, stringCol, null));
		}
		
		@Override
		public void visit(IntegerColumn intCol)
		{
			table.addColumn(new SQLiteIntegerColumn<Long>(SQLiteRecordStore.this, getColumnConstraint(intCol), schema, intCol, null));
		}
		
		@Override
		public void visit(FloatColumn floatCol)
		{
			table.addColumn(new SQLiteDoubleColumn<Double>(SQLiteRecordStore.this, getColumnConstraint(floatCol), schema, floatCol, null));
		}
		
		/**
		 * For now (until we implement a solution based on normalisation) we store the values of ListColumns as Blobs.
		 * 
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.BasicTableFactory#visitListColumn(uk.ac.ucl.excites.sapelli.storage.model.ListColumn)
		 */
		@Override
		public <L extends List<T>, T> void visitListColumn(final ListColumn<L, T> listCol)
		{
			table.addColumn(new SQLiteBlobColumn<L>(SQLiteRecordStore.this, getColumnConstraint(listCol), schema, listCol, new TypeMapping<byte[], L>()
			{

				@Override
				public byte[] toSQLType(L value)
				{
					BitOutputStream bos = null;
					try
					{
						ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
						bos = new BitWrapOutputStream(baos);
						listCol.writeValue(value, bos);
						bos.flush();
						return baos.toByteArray();
					}
					catch(Exception e)
					{
						e.printStackTrace(System.err);
						return null;
					}
					finally
					{
						if(bos != null)
							try
							{
								bos.close();
							}
							catch(IOException ignore) { }
					}
				}

				@Override
				public L toSapelliType(byte[] value)
				{
					BitInputStream bis = null;
					try
					{
						bis = new BitWrapInputStream(new ByteArrayInputStream(value));
						return listCol.readValue(bis);
					}
					catch(Exception e)
					{
						e.printStackTrace(System.err);
						return null;
					}
					finally
					{
						if(bis != null)
							try
							{
								bis.close();
							}
							catch(IOException ignore) { }
					}
				}
				
			}));
			
		}
		
	}
	
	/**
	 * @author mstevens
	 *
	 */
	protected class SelectROWIDHelper extends RecordByPrimaryKeyHelper
	{

		public SelectROWIDHelper(SQLiteTable table)
		{
			// Initialise
			super(table);
			
			// Build statement:			
			bldr.append("SELECT ROWID FROM");
			bldr.append(table.tableName);
			// WHERE clause:
			appendWhereClause(null);
			bldr.append(";", false);
		}
		
	}
	
}