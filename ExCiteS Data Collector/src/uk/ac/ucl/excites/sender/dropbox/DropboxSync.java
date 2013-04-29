package uk.ac.ucl.excites.sender.dropbox;

import java.io.File;

import uk.ac.ucl.excites.collector.project.model.MediaField;
import uk.ac.ucl.excites.sender.util.RecursiveFileObserver;
import uk.ac.ucl.excites.util.Debug;
import uk.ac.ucl.excites.util.FileHelpers;
import android.content.Context;
import android.os.FileObserver;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

/**
 * Class to define the Folder Sync and Upload to Dropbox
 * 
 * @author Michalis Vitos
 * 
 */
public class DropboxSync extends RecursiveFileObserver
{
	private static final int flags = FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE;

	private String basePath;
	
	// Dropbox Variables
	private DbxAccountManager mDbxAcctMgr;
	private DbxFileSystem dbxFs;

	// TODO Scan folder for files and upload them to the Dropbox if they don't exist

	/**
	 * Constructor takes the Folder to watch as a parameter
	 * 
	 * @param path
	 */
	public DropboxSync(Context context, File folder, String basePath)
	{
		super(folder.getAbsolutePath(), flags);
		this.basePath = basePath;
		
		Debug.d("Set up Dropbox Observer to folder: " + folder.getAbsolutePath());

		// Setup Dropbox
		try
		{
			Dropbox mDropbox = new Dropbox(context);

			if(mDropbox.hasLinkedAccount())
			{
				// Set up Dropbox
				mDbxAcctMgr = mDropbox.getDropboxManager();
				dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
				Debug.d("Dropbox has been linked.");
			}
			else
			{
				mDropbox.linkAccount();
			}
		}
		catch(Exception e)
		{
			Debug.e("DropboxSync() error: ", e);
		}
	}

	@Override
	public synchronized void onEvent(int event, String path)
	{

		// Debug.d("Event: " + event + " and path: " + path);

		// Make sure the path is not null
		if(path == null)
		{
			return;
		}

		// File to upload to Dropbox
		File fileToUpload = new File(path);

		// Check what was changed to the Projects Folder and upload or delete the file
		switch(event)
		{
		// Case used for new files
		case FileObserver.CREATE:
			Debug.d("File: " + fileToUpload + " was created but no action is taken.");
			// uploadFile(fileToUpload);
			break;

		// Case used for closed files
		case FileObserver.CLOSE_WRITE:
			Debug.d("File: " + fileToUpload + " was closed.");
			uploadFile(fileToUpload);
			break;

		// Case used for photos
		case FileObserver.MOVED_TO:
			Debug.d("File: " + fileToUpload + " was moved to.");
			uploadFile(fileToUpload);
			break;

		case FileObserver.DELETE:
			deleteFile(fileToUpload);
			// Log.i(MainActivity.TAG, "File: " + fileToUpload + " was deleted.");
			break;
		}
	}

	private void uploadFile(File fileToUpload)
	{
		DbxFile dropboxFile = null;
		try
		{
			// Path to the Dropbox Structure where to upload the file
			// fileToUplad - base path
			DbxPath dropboxPath = new DbxPath(fileToUpload.getParent().replace(basePath, "") + File.separator
					+ MediaField.getNonObfuscatedFilename(fileToUpload.getName()));

			Debug.d("File to be uploaded is: " + fileToUpload);
			Debug.d("Dropbox path to upload is: " + dropboxPath);

			Debug.d("File " + dropboxPath.getName() + " does " + (!dbxFs.isFile(dropboxPath) ? "not" : "") + " exist on the Dropbox Server.");

			// TODO Change the
			// TODO Get the image extension from the Media Class
			// for now we do not upload images
			// String image_ext = (new PhotoField()).getFileExtension();
			String image_ext = "jpg";
			
			// Upload only approved files
			if(!FileHelpers.getFileExtension(dropboxPath.getName()).equals(image_ext) && !dbxFs.isFile(dropboxPath))
			{
				dropboxFile = dbxFs.create(dropboxPath);
				// Upload the file to Dropbox
				dropboxFile.writeFromExistingFile(fileToUpload, false);
			}
			else
			{
				Debug.d("For now will not try to upload " + dropboxPath.getName() + " because it is an image");
			}

		}
		catch(Exception e)
		{
			Debug.e(e);
		}
		finally
		{
			if(dropboxFile != null)
				dropboxFile.close();
			Debug.d("File upload scheduled: " + fileToUpload.getName() + " Dropbox side name: " + MediaField.getNonObfuscatedFilename(fileToUpload.getName()));
		}
	}

	private void deleteFile(File fileToDelete)
	{
		// For now, do not do anything
	};
}