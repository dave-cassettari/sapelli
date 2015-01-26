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

package uk.ac.ucl.excites.sapelli.collector.activities;

import java.io.File;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.BuildConfig;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.fragments.ExportFragment;
import uk.ac.ucl.excites.sapelli.collector.load.AndroidProjectLoaderStorer;
import uk.ac.ucl.excites.sapelli.collector.load.ProjectLoader;
import uk.ac.ucl.excites.sapelli.collector.load.ProjectLoaderStorer;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.tasks.Backup;
import uk.ac.ucl.excites.sapelli.collector.ui.PagerAdapter;
import uk.ac.ucl.excites.sapelli.collector.util.AsyncDownloader;
import uk.ac.ucl.excites.sapelli.collector.util.AsyncTaskWithWaitingDialog;
import uk.ac.ucl.excites.sapelli.collector.util.DeviceID;
import uk.ac.ucl.excites.sapelli.collector.util.ProjectRunHelpers;
import uk.ac.ucl.excites.sapelli.collector.util.qrcode.IntentIntegrator;
import uk.ac.ucl.excites.sapelli.collector.util.qrcode.IntentResult;
import uk.ac.ucl.excites.sapelli.shared.db.StoreClient;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.StringUtils;
import uk.ac.ucl.excites.sapelli.shared.util.TransactionalStringBuilder;
import uk.ac.ucl.excites.sapelli.storage.eximport.xml.XMLRecordsImporter;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.astuetz.PagerSlidingTabStrip;
import com.crashlytics.android.Crashlytics;
import com.ipaulpro.afilechooser.utils.FileUtils;

/**
 * @author Julia, Michalis Vitos, mstevens
 * 
 */
public class ProjectManagerActivity extends ExportActivity implements StoreClient, DeviceID.InitialisationCallback, ProjectLoaderStorer.FileSourceCallback, AsyncDownloader.Callback, ListView.OnItemClickListener
{

	// STATICS--------------------------------------------------------
	static private final String TAG = "ProjectManagerActivity";

	static protected final String XML_FILE_EXTENSION = "xml";

	static private final String DEMO_PROJECT = "demo.excites";

	public static final int RETURN_BROWSE_FOR_PROJECT_LOAD = 1;
	public static final int RETURN_BROWSE_FOR_RECORD_IMPORT = 2;

	// DYNAMICS-------------------------------------------------------
	private ProjectStore projectStore;
	private Project selectedProject;
	private String[] projectsArray;
	private List<Project> parsedProjects;

	// UI
	private TextView addProjects;
	private PagerSlidingTabStrip tabs;
	private ViewPager pager;
	private int pageMargin;
	private PagerAdapter adapter;
	private Dialog encryptionDialog;
	private DeviceID deviceID;
	private ListView drawerList;
	private ActionBarDrawerToggle drawerToggle;
	private DrawerLayout drawerLayout;
	private Button runProject;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		if(app.getBuildInfo().isDemoBuild())
			return;
		//else ...
		// Only if not in demo mode:
		
		// Set-up UI...
		// Hide soft keyboard on create
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		setContentView(R.layout.activity_projectmanager);
		drawerList = (ListView) findViewById(R.id.left_drawer);
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
		pager = (ViewPager) findViewById(R.id.pager);
		pageMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
		addProjects = (TextView) findViewById(R.id.addProjects);
		runProject = (Button) findViewById(R.id.btn_runProject);

		addProjects.setOnClickListener(new OnClickListener() {

			@SuppressLint("ClickableViewAccessibility")
			@Override
			public void onClick(View v) {
				browse();
			}
		});

		// Set the drawer toggle as the DrawerListener
		drawerLayout.setDrawerListener(drawerToggle);
		// enable ActionBar icon to behave as action to toggle drawer
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {

			/** Called when a drawer has settled in a completely closed state. */
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}

			/** Called when a drawer has settled in a completely open state. */
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}
		};

		// Set the drawer toggle as the DrawerListener
		drawerLayout.setDrawerListener(drawerToggle);

		// Set the list's click listener
		drawerList.setOnItemClickListener(this);

	}

	public void browse() {
		// Use the GET_CONTENT intent from the utility class
		Intent target = FileUtils.createGetContentIntent();
		// Create the chooser Intent
		Intent intent = Intent.createChooser(target, getString(R.string.chooseSapelliFile));
		try {
			startActivityForResult(intent, RETURN_BROWSE_FOR_PROJECT_LOAD);
		} catch (ActivityNotFoundException e) {
		}
	}

	public void browse(MenuItem item) {
		browse();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		
		// Initialise DeviceID:
		DeviceID.Initialise(this, this); // will post a callback upon completion (success/failure)

		// Get ProjectStore instance:
		try
		{
			projectStore = app.getProjectStore(this);
		}
		catch(Exception e)
		{
			showErrorDialog(getString(R.string.projectStorageAccessFail, ExceptionHelpers.getMessageAndCause(e)), true);
			return;
		}
		
		// And finally...
		if(app.getBuildInfo().isDemoBuild())
			demoMode();
		else
			new RetrieveProjectsTask().execute(); // Update project list
		// TODO remember & re-select last selected project

		// stop tracing
		Debug.stopMethodTracing();
	}

	@Override
	public void initialisationSuccess(DeviceID deviceID)
	{
		this.deviceID = deviceID;
		if(!BuildConfig.DEBUG)
		{
			Crashlytics.setLong(CollectorApp.CRASHLYTICS_DEVICE_ID_CRC32, deviceID.getIDAsCRC32Hash());
			Crashlytics.setString(CollectorApp.CRASHLYTICS_DEVICE_ID_MD5, deviceID.getIDAsMD5Hash().toString());
		}
	}

	@Override
	public void initialisationFailure(DeviceID deviceID)
	{
		deviceID.printInfo();
		showErrorDialog(R.string.noDeviceID, true);
	}

	private void demoMode()
	{
		try
		{
			List<Project> projects = projectStore.retrieveProjects();
			Project p = null;
			if(projects.isEmpty())
			{	// Use /mnt/sdcard/Sapelli/ as the basePath:
				p = new ProjectLoader(fileStorageProvider).load(this.getAssets().open(DEMO_PROJECT, AssetManager.ACCESS_RANDOM));
				projectStore.add(p);
			}
			else
				p = projects.get(0); // Assumption: there is only one stored project in demo mode
			// Run the project
			startActivity(ProjectRunHelpers.getProjectRunIntent(this, p));
		}
		catch(Exception e)
		{
			Log.e(TAG, "Error loading/storing/launching demo project", e);
			showErrorDialog(R.string.demoLoadFail, true);
		}
	}

	@Override
	protected void onDestroy()
	{
		// clean up:
		app.discardStoreUsage(projectStore, this); // signal that the activity no longer needs the DAO
		// super:
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.projectmanager, menu);
		if (parsedProjects != null)
			menu.findItem(R.id.action_remove).setVisible(!parsedProjects.isEmpty());
		return true;
	}

	public boolean openSenderSettings(MenuItem item)
	{
		// TODO Re-enable the service at same point
		// startActivity(new Intent(getBaseContext(), DataSenderPreferences.class));
		return true;
	}

	@SuppressLint("InflateParams")
	public boolean openAboutDialog(MenuItem item)
	{
		// Set-up UI:
		View view = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
		TextView infoLbl = (TextView) view.findViewById(R.id.aboutInfo);
		infoLbl.setClickable(true);
		infoLbl.setMovementMethod(LinkMovementMethod.getInstance());
		infoLbl.setText(Html.fromHtml(
				"<p><b>" + app.getBuildInfo().getVersionInfo() + "</b></p>" +
				"<p>" + app.getBuildInfo().getBuildInfo() + ".</p>" +
				"<p>" + getString(R.string.by_ucl_excites_html)  + "</p>" + 
				"<p>" + getString(R.string.license)  + "</p>" +
				"<p>" + "Device ID (CRC32): " + (deviceID != null ? deviceID.getIDAsCRC32Hash() : "?") + ".</p>"));	
		infoLbl.setPadding(2, 2, 6, 2);
		ImageView iconImg = (ImageView) view.findViewById(R.id.aboutIcon);
		iconImg.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://sapelli.org")));
			}
		});
		
		// Set-up dialog:
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		dialogBuilder.setPositiveButton(getString(android.R.string.ok), null); // click will dismiss the dialog (BACK press will too)
		AlertDialog aboutDialog = dialogBuilder.create();
		aboutDialog.setView(view, 0, 10, 0, 0);
		aboutDialog.setTitle(R.string.software_and_device_info);

		// Show the dialog:
		aboutDialog.show();

		return true;
	}

	// Export all projects!!
	public boolean exportRecords(MenuItem item) {
		ExportFragment exportFragment = ExportFragment.newInstance(true);
		exportFragment.show(getSupportFragmentManager(), TAG);
		getSupportFragmentManager().executePendingTransactions();
		exportFragment.getExportLayout().setBackgroundResource(0);
		exportFragment.getDialog().setTitle(R.string.exportAllProj);

		// Grab the window of the dialog, and change the width
		WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
		Window window = exportFragment.getDialog().getWindow();
		lp.copyFrom(window.getAttributes());
		// This makes the dialog take up the full width
		lp.width = WindowManager.LayoutParams.MATCH_PARENT;
		lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
		window.setAttributes(lp);

		return true;
	}

	public boolean importRecords(MenuItem item)
	{
		// Use the GET_CONTENT intent from the utility class
		Intent target = FileUtils.createGetContentIntent();
		// Create the chooser Intent
		Intent intent = Intent.createChooser(target, "Choose an XML file");
		try
		{
			startActivityForResult(intent, RETURN_BROWSE_FOR_RECORD_IMPORT);
		}
		catch(ActivityNotFoundException e){}

		return true;
	}
	
	/**
	 * Create Sapelli back-up package
	 * 
	 * @param item
	 * @return
	 */
	public boolean backupSapelli(MenuItem item)
	{
		Backup.Run(this, fileStorageProvider, app);
		return true;
	}

	// public void browse(View view) {
	// // Use the GET_CONTENT intent from the utility class
	// Intent target = FileUtils.createGetContentIntent();
	// // Create the chooser Intent
	// Intent intent = Intent.createChooser(target, getString(R.string.chooseSapelliFile));
	// try {
	// startActivityForResult(intent, RETURN_BROWSE_FOR_PROJECT_LOAD);
	// } catch (ActivityNotFoundException e) {
	// }
	// }

	/**
	 * Retrieve all parsed projects from db and populate tabs
	 */
	public void populateTabs() {
		projectsArray = new String[parsedProjects.size()];
		for (int i = 0; i < parsedProjects.size(); i++) {
			projectsArray[i] = parsedProjects.get(i).getName() + " " + parsedProjects.get(i).getVersion();
		}

		// Set the adapter for the list view
		drawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, projectsArray));

		adapter = new PagerAdapter(getSupportFragmentManager());
		pager.setAdapter(adapter);
		if (!parsedProjects.isEmpty()) {
			getSupportActionBar().setTitle(parsedProjects.get(0).getName());
			selectedProject = parsedProjects.get(0);
			pager.setPageMargin(pageMargin);
			tabs.setViewPager(pager);
			tabs.setVisibility(View.VISIBLE);
			pager.setVisibility(View.VISIBLE);
			runProject.setVisibility(View.VISIBLE);
			addProjects.setVisibility(View.GONE);
		} else {
			getSupportActionBar().setTitle(R.string.app_name);
			tabs.setVisibility(View.GONE);
			pager.setVisibility(View.GONE);
			runProject.setVisibility(View.GONE);
			addProjects.setVisibility(View.VISIBLE);

		}

		supportInvalidateOptionsMenu();
	}

	public Project getSelectedProject(boolean errorIfNull) {
		if (selectedProject == null) {
			if (errorIfNull)
				showErrorDialog(R.string.selectProject, false);
			return null;
		}
		return selectedProject;
	}

	public void runProject(View view)
	{
		Project p = getSelectedProject(true);
		if(p != null)
			startActivity(ProjectRunHelpers.getProjectRunIntent(this, p));
	}

	private void removeProject()
	{
		Project project = getSelectedProject(false);
		if(project == null)
			return;
		
		// Remove project from store:
		projectStore.delete(project);
		
		// Remove installation folder:
		org.apache.commons.io.FileUtils.deleteQuietly(fileStorageProvider.getProjectInstallationFolder(project, false));
		
		// Remove shortcut:
		ProjectRunHelpers.removeShortcut(this, project);
		
		// Refresh list:
		new RetrieveProjectsTask().execute();

		// TODO Re-enable the service at same point
		// Restart the DataSenderService to stop monitoring the deleted project
		// ServiceChecker.restartActiveDataSender(this);
	}

	public void loadProject(String path)
	{
		String location = path.trim();
		if(location.isEmpty())
			// Download Sapelli file if path is a URL
			showErrorDialog(R.string.pleaseSelect);
		else
		{
			// Extract & parse a local Sapelli file
//			txtProjectPathOrURL.setText("");

			// Add project
			if(Patterns.WEB_URL.matcher(location).matches())
				// Location is a (remote) URL: download Sapelli file:
				AsyncDownloader.Download(this, fileStorageProvider.getSapelliDownloadsFolder(), location, this); // loading & store of the project will happen upon successful download (via callback)
			else if(location.toLowerCase().endsWith("." + XML_FILE_EXTENSION))
				// Warn about bare XML file (no longer supported):
				showErrorDialog(R.string.noBareXMLProjects);
			else
			{
				File localFile = new File(location);
				if(ProjectLoader.HasSapelliFileExtension(localFile))
					// Load & store project from local file:
					new AndroidProjectLoaderStorer(this, fileStorageProvider, projectStore).loadAndStore(localFile, Uri.fromFile(localFile).toString(), this);
				else
					showErrorDialog(getString(R.string.unsupportedExtension, FileHelpers.getFileExtension(localFile),
							StringUtils.join(ProjectLoader.SAPELLI_FILE_EXTENSIONS, ", ")));
				// Use the path where the xml file resides as the basePath (img&snd folders are assumed to be in the same place), no subfolders are created:
				// Show parser warnings if needed:
			}
		}
	}
	
	public void scanQR(View view)
	{
		// Start the Intent to Scan a QR code
		IntentIntegrator integrator = new IntentIntegrator(this);
		integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
	}
	
	/**
	 * Create a shortcut
	 * 
	 */
	public boolean createShortcut()
	{
		// Get the selected project
		Project selectedProject = getSelectedProject(true);
		if(selectedProject != null)
			ProjectRunHelpers.createShortcut(this, fileStorageProvider, selectedProject);
		return true;
	}

	/**
	 * Remove a shortcut
	 * 
	 */
	public boolean removeShortcut()
	{
		// Get the selected project
		Project selectedProject = getSelectedProject(true);
		if(selectedProject != null)
			ProjectRunHelpers.removeShortcut(this, selectedProject);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		Uri uri = null;
		String path = null;

		if(resultCode == Activity.RESULT_OK)
			switch(requestCode)
			{
			// File browse dialog for project loading:
			case RETURN_BROWSE_FOR_PROJECT_LOAD:

				uri = data.getData();

				// Get the File path from the Uri
				path = FileUtils.getPath(this, uri);

				loadProject(path);
				break;

			// File browse dialog for record importing:
			case RETURN_BROWSE_FOR_RECORD_IMPORT:

				uri = data.getData();

				// Get the File path from the Uri
				path = FileUtils.getPath(this, uri);

				// Alternatively, use FileUtils.getFile(Context, Uri)
				if(path != null && FileUtils.isLocal(path))
				{
					try
					{ // TODO make import & storage async
						// Import:
						XMLRecordsImporter importer = new XMLRecordsImporter(app.getCollectorClient());
						List<Record> records = importer.importFrom((new File(path)).getAbsoluteFile());

						// Show parser warnings if needed:
						List<String> warnings = importer.getWarnings(); 
						if(!warnings.isEmpty())
						{
							TransactionalStringBuilder bldr = new TransactionalStringBuilder("\n");
							bldr.append(getString(R.string.parsingWarnings) + ":");
							for(String warning : warnings)
								bldr.append(" - " + warning);
							showWarningDialog(bldr.toString());
						}

						/*
						 * //TEST CODE (export again to compare with imported file): RecordsExporter exporter = new RecordsExporter(((CollectorApp) getApplication()).getDumpFolderPath(), dao); exporter.export(records);
						 */

						// Store the records:
						// for(Record r : records)
						// dao.store(r); //TODO avoid duplicates!

						// User feedback:
						showInfoDialog("Succesfully imported " + records.size() + " records."); // TODO report skipped duplicates
					} catch (Exception e) {
						showErrorDialog("Error upon importing records: " + e.getMessage(), false);
					}
				}

				break;
			// QR Reader
			case IntentIntegrator.REQUEST_CODE:
				IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
				if (scanResult != null) {
					String fileUrl = data.getStringExtra("SCAN_RESULT");
					loadProject(fileUrl);
				}
				break;
			}
	}

	/**
	 * Dialog to check whether it is desired to remove project
	 * 
	 * @param view
	 */
	public void removeDialog(View view)
	{
		Project project = getSelectedProject(true);
		if(project != null)
		{
			showYesNoDialog(R.string.project_manager, R.string.removeProjectConfirm, false, new Runnable()
			{
				@Override
				public void run()
				{
					removeProject();
				}
			}, false);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if(encryptionDialog != null)
			encryptionDialog.dismiss();
	}
	
	@Override
	public void downloadSuccess(String downloadUrl, File downloadedFile)
	{
		new AndroidProjectLoaderStorer(this, fileStorageProvider, projectStore).loadAndStore(downloadedFile, downloadUrl, this);
	}

	@Override
	public void downloadFailure(String downloadUrl, Exception cause)
	{
		showErrorDialog(R.string.downloadError, false);
	}
	
	@Override
	public void projectLoadStoreSuccess(File sapelliFile, String sourceURI, Project project, List<String> warnings)
	{
		// Deal with downloaded file...
		if(Patterns.WEB_URL.matcher(sourceURI).matches())
			// Change temporary name to proper one:
			sapelliFile.renameTo(new File(sapelliFile.getParentFile().getAbsolutePath() + File.separator + FileHelpers.makeValidFileName(project.toString(false) + ".sapelli")));
		
		// Warnings...
		TransactionalStringBuilder bldr = new TransactionalStringBuilder("\n");
		//	Parser/loader warnings: 
		if(!warnings.isEmpty())
		{
			bldr.append(getString(R.string.projectLoadingWarnings) + ":");
			for(String warning : warnings)
				bldr.append(" - " + warning);
		}
		//	Check file dependencies:
		List<String> missingFiles = project.getMissingFilesRelativePaths(fileStorageProvider);
		if(!missingFiles.isEmpty())
		{
			bldr.append(getString(R.string.missingFiles) + ":");
			for(String missingFile : missingFiles)
				bldr.append(" - " + missingFile);
		}
		//	Show warnings dialog:
		if(!bldr.isEmpty())
			showWarningDialog(bldr.toString()); // no need to worry about message not fitting the dialog, it will have a scrollbar when necessary 
		
		// Update project list:
		new RetrieveProjectsTask().execute();

		// TODO Re-enable the service at same point
		// Restart the DataSenderService to start monitoring the new project
		// ServiceChecker.restartActiveDataSender(this);
	}

	@Override
	public void projectLoadStoreFailure(File sapelliFile, String sourceURI, Exception cause)
	{
		// Deal with downloaded file...
		if(Patterns.WEB_URL.matcher(sourceURI).matches())
			org.apache.commons.io.FileUtils.deleteQuietly(sapelliFile);
		
		// Report problem:
		Log.e(TAG, "Could not load/store Sapelli file", cause);
		showErrorDialog(getString(R.string.sapelliFileLoadFailure, (Patterns.WEB_URL.matcher(sourceURI).matches() ? sapelliFile.getAbsolutePath() : sourceURI), ExceptionHelpers.getMessageAndCause(cause)), false);		
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		selectedProject = parsedProjects.get(position);
		if (!parsedProjects.isEmpty())
			getSupportActionBar().setTitle(selectedProject.getName());
		else
			getSupportActionBar().setTitle(R.string.app_name);
		drawerLayout.closeDrawer(drawerList);

	}

	private class RetrieveProjectsTask extends AsyncTaskWithWaitingDialog<Void, List<Project>>
	{

		public RetrieveProjectsTask()
		{
			super(ProjectManagerActivity.this, getString(R.string.load_projects));
		}

		@Override
		protected List<Project> doInBackground(Void... params)
		{
			return projectStore.retrieveProjects();
		}

		@Override
		protected void onPostExecute(List<Project> result)
		{
			parsedProjects = result;
			super.onPostExecute(result); // dismiss dialog
			populateTabs();

		}

	}

}