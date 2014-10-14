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

package uk.ac.ucl.excites.sapelli.collector.io;

import java.io.File;

import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;

/**
 * Class which to manages (almost) all path generation/resolving and folder creation for different types of file storage needs
 * 
 * @author mstevens, Michalis Vitos
 */
public class FileStorageProvider
{
	
	// Folders to be used by Sapelli
	public static enum Folders
	{
		/**
		 * Folder for file data (e.g. media attachments) produced by the collector (grouped per project)
		 */
		Data,
		
		/**
		 * Folder for downloads
		 */
		Downloads,
		
		/**
		 * Folder in which database copies and stacktraces are placed
		 */
		Dumps,
		
		/**
		 * Folder for record exports
		 */
		Export,

		/**
		 * Folder for log files, both project-specific and general
		 */
		Logs,

		/**
		 * Folder in which projects are installed
		 */
		Projects,
		
		/**
		 * Folder for temporary files
		 */
		Temp
	}

	private final File sapelliFolder;
	
	public FileStorageProvider(File sapelliFolder)
	{
		if(sapelliFolder == null)
			throw new NullPointerException("SapelliFolder cannot be null!");
		if(!sapelliFolder.exists() || !sapelliFolder.isDirectory())
			throw new FileStorageException("No an existing directory (" + sapelliFolder.getAbsolutePath() + ")");
		this.sapelliFolder = sapelliFolder;
	}
	
	public File getSapelliFolder() throws FileStorageException
	{
		if(sapelliFolder.exists() && sapelliFolder.canRead())
			return sapelliFolder;
		else
			throw new FileStorageException("Sapelli folder is not or no longer accessible (path: " + sapelliFolder.getAbsolutePath());
	}
	
	/**
	 * @return absolute path to Sapelli folder, including trailing file separator (/ or \)
	 */
	public String getSapelliFolderPath() throws FileStorageException
	{
		return getSapelliFolder().getAbsolutePath() + File.separator;
	}
	
	/**
	 * @param parent
	 * @param project
	 * @param create
	 * @return
	 * @throws FileStorageException
	 */
	protected File getProjectSpecificSubFolder(File parent, Project project, boolean create) throws FileStorageException
	{
		return getProjectSpecificSubFolder(parent, project.getName(), project.getVariant(), project.getVersion(), create);
	}
	
	/**
	 * @param parent
	 * @param projectName
	 * @param projectVariant
	 * @param projectVersion
	 * @param create
	 * @return
	 * @throws FileStorageException
	 */
	protected File getProjectSpecificSubFolder(File parent, String projectName, String projectVariant, String projectVersion, boolean create) throws FileStorageException
	{
		return createIfNeeded(
				// (PARENT]/(projectName)[ (projectVariant)]/v(projectVersion)
				parent.getAbsolutePath() + File.separatorChar + projectName + (projectVariant != null ? " " + projectVariant : "") + File.separatorChar + "v" + projectVersion + File.separatorChar,
				create);
	}
	
	public File getProjectsFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Projects.name(), create);
	}

	public File getProjectInstallationFolder(Project project, boolean create) throws FileStorageException
	{	
		return getProjectSpecificSubFolder(getProjectsFolder(create), project, create);
	}
	
	public File getProjectInstallationFolder(String projectName, String projectVariant, String projectVersion, boolean create) throws FileStorageException
	{	
		return getProjectSpecificSubFolder(getProjectsFolder(create), projectName, projectVariant, projectVersion, create);
	}
	
	public File getDownloadsFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Downloads.name(), create);
	}
	
	public File getDumpFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Dumps.name(), create);
	}
	
	public File getTempFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Temp.name(), create);
	}
	
	public File getExportFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Export.name(), create);
	}

	public File getDataFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Data.name(), create);
	}
	
	public File getProjectDataFolder(Project project, boolean create) throws FileStorageException
	{
		return getProjectSpecificSubFolder(getDataFolder(create), project, create);
	}
	
	public File getLogsFolder(boolean create) throws FileStorageException
	{
		return createIfNeeded(getSapelliFolder().getAbsolutePath() + File.separator + Folders.Logs.name(), create);
	}
	
	public File getProjectLogsFolder(Project project, boolean create) throws FileStorageException
	{
		return getProjectSpecificSubFolder(getLogsFolder(create), project, create);
	}
	
	private File createIfNeeded(String folderPath, boolean create) throws FileStorageException
	{
		File folder = new File(folderPath);
		
		// Create and test the folder
		if(create)
		{
			if(!FileHelpers.createFolder(folder))
				throw new FileStorageException("Could not create folder: " + folder.getAbsolutePath());
		}
		return folder;
	}
	
}
