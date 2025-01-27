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

package uk.ac.ucl.excites.sapelli.collector.db.db4o;

import java.io.File;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.model.ProjectDescriptor;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Relationship;
import uk.ac.ucl.excites.sapelli.shared.db.StoreBackupper;
import uk.ac.ucl.excites.sapelli.shared.db.db4o.DB4OConnector;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Predicate;

/**
 * @author mstevens, julia, Michalis Vitos
 * 
 */
public class DB4OProjectStore extends ProjectStore
{

	// Statics----------------------------------------------
	static public final int ACTIVATION_DEPTH = 40;
	static public final int UPDATE_DEPTH = 40;

	// Dynamics---------------------------------------------
	private ObjectContainer db4o;
	private String filename;
	
	public DB4OProjectStore(File folder, String baseFilename) throws Exception
	{
		this.filename = baseFilename + DATABASE_NAME_SUFFIX;
		this.db4o = DB4OConnector.open(DB4OConnector.getFile(folder, filename), Project.class, HeldForeignKey.class);
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#retrieveV1Project(int, int)
	 */
	@Override
	public Project retrieveV1Project(final int schemaID, final int schemaVersion)
	{
		@SuppressWarnings("serial")
		ObjectSet<Project> result = db4o.query(new Predicate<Project>()
		{
			public boolean match(Project project)
			{
				return 	project.isV1xProject() &&
						project.getID() == schemaID &&
						project.getV1XSchemaVersion() == schemaVersion;
			}
		});
		if(result.isEmpty())
			return null;
		else
		{
			Project p = result.get(0);
			db4o.activate(p, ACTIVATION_DEPTH);
			return p;
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#doAdd(uk.ac.ucl.excites.sapelli.collector.model.Project)
	 */
	@Override
	public void doAdd(Project project)
	{
		db4o.store(project);
		db4o.commit();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#retrieveProjects()
	 */
	@Override
	public List<Project> retrieveProjects()
	{
		final List<Project> result = db4o.queryByExample(Project.class);
		for(Project p : result)
			db4o.activate(p, ACTIVATION_DEPTH);
		return result;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#retrieveProject(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Project retrieveProject(final String name, final String variant, final String version)
	{
		@SuppressWarnings("serial")
		ObjectSet<Project> result = db4o.query(new Predicate<Project>()
		{
			public boolean match(Project project)
			{
				return 	project.getName().equalsIgnoreCase(name) &&
						(variant != null ? variant.equals(project.getVariant()) : true) &&
						project.getVersion().equalsIgnoreCase(version);
			}
		});
		if(result.isEmpty())
			return null;
		else
		{
			Project p = result.get(0);
			db4o.activate(p, ACTIVATION_DEPTH);
			return p;
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#retrieveProjectVersions(int)
	 */
	@Override
	public List<Project> retrieveProjectVersions(final int projectID)
	{
		@SuppressWarnings("serial")
		ObjectSet<Project> result = db4o.query(new Predicate<Project>()
		{
			public boolean match(Project project)
			{
				return project.getID() == projectID;
			}
		});
		for(Project p : result)
			db4o.activate(p, ACTIVATION_DEPTH);
		return result;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#retrieveProject(int, int)
	 */
	@Override
	public Project retrieveProject(int projectID, int projectFingerPrint)
	{
		for(Project p : retrieveProjectVersions(projectID))
			if(p.getFingerPrint() == projectFingerPrint)
				return p;
		return null;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.db.ProjectStore#delete(uk.ac.ucl.excites.sapelli.collector.model.Project)
	 */
	@Override
	public void delete(Project project)
	{
		db4o.delete(project);
		db4o.commit();
	}

	@Override
	public void storeHeldForeignKey(Relationship relationship, RecordReference foreignKey)
	{
		if(!relationship.isHoldForeignRecord())
			throw new IllegalArgumentException("This relationship is not allowed to hold on to foreign records");
		if(retrieveHeldForeignKey(relationship) != null)
			deleteHeldForeignKey(relationship);
		db4o.store(new HeldForeignKey(relationship, foreignKey));
		db4o.commit();
	}

	@Override
	public RecordReference retrieveHeldForeignKey(Relationship relationship)
	{
		if(!relationship.isHoldForeignRecord())
			throw new IllegalArgumentException("This relationship is not allowed to hold on to foreign records");
		HeldForeignKey heldFK = retrieveHeldForeignKeyObj(relationship);
		return heldFK != null ? heldFK.foreignKey : null; 

	}
	
	@Override
	public void deleteHeldForeignKey(Relationship relationship)
	{
		// Don't check for isHoldForeignRecord() here!
		HeldForeignKey heldFK = retrieveHeldForeignKeyObj(relationship);
		if(heldFK != null)
		{
			db4o.delete(heldFK);
			db4o.commit();
		}
	}
	
	private HeldForeignKey retrieveHeldForeignKeyObj(final Relationship relationship)
	{
		@SuppressWarnings("serial")
		ObjectSet<HeldForeignKey> result = db4o.query(new Predicate<HeldForeignKey>()
		{
			public boolean match(HeldForeignKey heldFK)
			{
				return heldFK.relationship == relationship;
			}
		});
		if(result.isEmpty())
			return null;
		else
		{
			HeldForeignKey heldFK = result.get(0);
			db4o.activate(heldFK, ACTIVATION_DEPTH);
			return heldFK;
		}
	}
	
	private class HeldForeignKey
	{
		
		Relationship relationship;
		RecordReference foreignKey;
		/**
		 * @param relationship
		 * @param foreignKey
		 */
		public HeldForeignKey(Relationship relationship, RecordReference foreignKey)
		{
			this.relationship = relationship;
			this.foreignKey = foreignKey;
		}
		
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Project> retrieveProjectsOrDescriptors()
	{
		return retrieveProjects();
	}

	@Override
	public Project retrieveProject(ProjectDescriptor descriptor)
	{
		return retrieveProject(descriptor.getID(), descriptor.getFingerPrint());
	}

	@Override
	public void delete(ProjectDescriptor projectDescriptor)
	{
		delete(retrieveProject(projectDescriptor));
	}
	
	@Override
	protected void doClose()
	{
		db4o.close();
	}

	@Override
	public void backup(StoreBackupper backuper, File destinationFolder) throws DBException
	{
		try
		{
			db4o.commit();
			File backupDB = backuper.isLabelFilesAsBackup() ?
				DB4OConnector.getFile(destinationFolder, filename + BACKUP_SUFFIX + TimeUtils.getTimestampForFileName()) :
				DB4OConnector.getFile(destinationFolder, filename);
			db4o.ext().backup(backupDB.getAbsolutePath());
		}
		catch(Exception e)
		{
			throw new DBException("Error upon backup up project store");
		}
	}
	
}
