package uk.ac.ucl.excites.collector.ui.fieldviews;

import java.io.File;
import java.io.FileOutputStream;

import uk.ac.ucl.excites.collector.ProjectController;
import uk.ac.ucl.excites.collector.R;
import uk.ac.ucl.excites.collector.media.CameraController;
import uk.ac.ucl.excites.collector.project.model.Field;
import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.PhotoField;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.ui.FieldUI;
import uk.ac.ucl.excites.collector.ui.CollectorView;
import uk.ac.ucl.excites.collector.ui.animation.PressAnimator;
import uk.ac.ucl.excites.collector.ui.picker.PickerAdapter;
import uk.ac.ucl.excites.collector.ui.picker.PickerView;
import uk.ac.ucl.excites.collector.ui.picker.items.FileImageItem;
import uk.ac.ucl.excites.collector.ui.picker.items.Item;
import uk.ac.ucl.excites.collector.ui.picker.items.ResourceImageItem;
import uk.ac.ucl.excites.collector.util.ColourHelpers;
import uk.ac.ucl.excites.collector.util.ScreenMetrics;
import uk.ac.ucl.excites.util.FileHelpers;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.ViewSwitcher;

/**
 * Built-in camera view
 * 
 * TODO Fix white "blocks" briefly appearing where the button(s) should be when view is loaded for 2nd time (on Android v2.x only?)
 * 
 * @author mstevens, Michalis Vitos
 */
@SuppressLint("ViewConstructor")
public class CameraView extends ViewSwitcher implements FieldUI, AdapterView.OnItemClickListener, PictureCallback
{

	static private final String TAG = "CameraView";
	static private final int PREVIEW_SIZE = 1024;
	
	private CollectorView collectorView;

	private ProjectController controller;
	private Project project;
	private PhotoField photoField;

	private CameraController cameraController;

	private RelativeLayout captureLayout;
	private SurfaceView captureView;
	private RelativeLayout reviewLayout;
	private ImageView reviewView;
	private RelativeLayout.LayoutParams buttonParams;

	private volatile boolean handlingClick;

	private byte[] reviewPhotoData;
	private HandleImage handleImage;

	@SuppressWarnings("deprecation")
	public CameraView(Context context, CollectorView collectorView, ProjectController controller, Field field)
	{
		super(context);
		this.collectorView = collectorView;
		this.controller = controller;
		this.project = controller.getProject();
		this.photoField = (PhotoField) field;
		
		// Capture UI:
		captureLayout = new RelativeLayout(context);
		captureView = new SurfaceView(context);
		captureLayout.addView(captureView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		// buttons are add in initialise
		this.addView(captureLayout);

		// Review UI:
		reviewLayout = new RelativeLayout(context);
		reviewView = new ImageView(context);
		reviewView.setScaleType(ScaleType.FIT_CENTER);
		reviewLayout.addView(reviewView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		// buttons are add in initialise
		this.addView(reviewLayout);

		// Layout parameters for the buttons:
		buttonParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); // You might want to tweak these to WRAP_CONTENT
		buttonParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		
		// Camera controller & camera selection:
		cameraController = new CameraController(photoField.isUseFrontFacingCamera());
		if(!cameraController.foundCamera())
		{ // no camera found, try the other one:
			cameraController.findCamera(!photoField.isUseFrontFacingCamera());
			if(!cameraController.foundCamera())
			{ // still no camera, this device does not seem to have one:
				controller.mediaDone(null);
				return;
			}
		}
		// Set flash mode:
		cameraController.setFlashMode(photoField.getFlashMode());

		// Set-up surface holder:
		SurfaceHolder holder = captureView.getHolder();
		holder.addCallback(cameraController);
		holder.setKeepScreenOn(true);
		// !!! Deprecated but cameraController preview crashes without it (at least on the XCover/Gingerbread):
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		// Buttons
		captureLayout.addView(new CaptureButtonView(getContext()), buttonParams);
		reviewLayout.addView(new ReviewButtonView(getContext()), buttonParams);
	}

	@Override
	public void update()
	{
		this.handlingClick = false;
		// Switch back to capture layout if needed:
		if(getCurrentView() == reviewLayout)
			showPrevious();
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera)
	{
		this.reviewPhotoData = data;

		handleImage = new HandleImage(data, reviewView);
		handleImage.execute();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
	{
		// Task to perform after animation has finished:
		Runnable action = new Runnable()
		{
			public void run()
			{
				if(!handlingClick) // only handle one click at the time
				{
					handlingClick = true;
					if(getCurrentView() == captureLayout)
					{ // in Capture mode --> there is (currently) only one button here: the one to take a photo
						cameraController.takePicture(CameraView.this);
					}
					else
					{ // in Review mode --> there are 2 buttons: approve (pos=0) & discard (pos=1)
						if(position == 0)
						{ // photo approved
							try
							{ // Save photo to file:
								File photoFile = photoField.getNewTempFile(controller.getCurrentRecord());
								FileOutputStream fos = new FileOutputStream(photoFile);
								fos.write(reviewPhotoData);
								fos.close();
								controller.mediaDone(photoFile);
							}
							catch(Exception e)
							{
								Log.e(TAG, "Could not save photo.", e);
								controller.mediaDone(null);
							}
							finally
							{
								cameraController.close();
							}
						}
						else
						// if(position == 1)
						{ // photo discarded
							showNext(); // switch back to capture mode
							cameraController.startPreview();
							handlingClick = false;
						}
						reviewPhotoData = null;
					}
				}
			}
		};

		// Execute the "press" animation if allowed, then perform the action: 
		if(controller.getCurrentForm().isAnimation())
			(new PressAnimator(action, v, collectorView)).execute(); //execute animation and the action afterwards
		else
			action.run(); //perform task now (animation is disabled)
	}
	
	@Override
	public void cancel()
	{
		cameraController.close();
	}	

	@Override
	public Field getField()
	{
		return photoField;
	}

	/**
	 * @author mstevens
	 */
	private class CaptureButtonView extends CameraButtonView
	{

		public CaptureButtonView(Context context)
		{
			super(context);
		}

		@Override
		protected int getNumberOfColumns()
		{
			return 1;
		}

		/**
		 * Note: for now there is only the capture button, we might add other features (flash, zooming, etc.) later
		 * 
		 * @see uk.ac.ucl.excites.collector.ui.fieldviews.CameraView.CameraButtonView#addButtons()
		 */
		@Override
		protected void addButtons()
		{
			// Capture button:
			Item captureButton = null;
			File captureImgFile = project.getImageFile(photoField.getCaptureButtonImageRelativePath());
			if(FileHelpers.isReadableFile(captureImgFile))
				captureButton = new FileImageItem(captureImgFile);
			else
				captureButton = new ResourceImageItem(getContext().getResources(), R.drawable.take_photo);
			captureButton.setBackgroundColor(ColourHelpers.ParseColour(photoField.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
			addButton(captureButton);
		}
	}

	/**
	 * @author mstevens
	 */
	private class ReviewButtonView extends CameraButtonView
	{

		public ReviewButtonView(Context context)
		{
			super(context);
		}

		@Override
		protected int getNumberOfColumns()
		{
			return 2;
		}

		@Override
		protected void addButtons()
		{
			// Approve button:
			Item approveButton = null;
			File approveImgFile = project.getImageFile(photoField.getApproveButtonImageRelativePath());
			if(FileHelpers.isReadableFile(approveImgFile))
				approveButton = new FileImageItem(approveImgFile);
			else
				approveButton = new ResourceImageItem(getContext().getResources(), R.drawable.accept);
			approveButton.setBackgroundColor(ColourHelpers.ParseColour(photoField.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
			addButton(approveButton);
			
			// Discard button:
			Item discardButton = null;
			File discardImgFile = project.getImageFile(photoField.getDiscardButtonImageRelativePath());
			if(FileHelpers.isReadableFile(discardImgFile))
				discardButton = new FileImageItem(discardImgFile);
			else
				discardButton = new ResourceImageItem(getContext().getResources(), R.drawable.delete);
			discardButton.setBackgroundColor(ColourHelpers.ParseColour(photoField.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
			addButton(discardButton);
		}

	}

	/**
	 * @author mstevens
	 */
	private abstract class CameraButtonView extends PickerView
	{

		static public final float BUTTON_HEIGHT_DIP = 64;

		private int buttonSize; // width = height
		private int buttonPadding;
		private int buttonBackColor;
		
		/**
		 * @param context
		 */
		public CameraButtonView(Context context)
		{
			super(context);
					
			setOnItemClickListener(CameraView.this);

			// Layout:
			setBackgroundColor(Color.TRANSPARENT);
			setGravity(Gravity.CENTER);
			setPadding(0, 0, 0, CameraView.this.collectorView.getSpacingPx());

			// Columns
			setNumColumns(getNumberOfColumns());

			// Images/buttons:
			pickerAdapter = new PickerAdapter(super.getContext());

			// Button size, padding & background colour:
			this.buttonSize = ScreenMetrics.ConvertDipToPx(context, BUTTON_HEIGHT_DIP);
			this.buttonPadding = ScreenMetrics.ConvertDipToPx(context, ChoiceView.PADDING_DIP);
			this.buttonBackColor = ColourHelpers.ParseColour(controller.getCurrentForm().getButtonBackgroundColor(), Form.DEFAULT_BUTTON_BACKGROUND_COLOR /*light gray*/);
			
			// The addButtons() should be called after the button parameters (size, padding etc.) have been setup
			addButtons();

			// And finally:
			setAdapter(pickerAdapter);
		}
		
		protected void addButton(Item button)
		{
			button.setWidthPx(buttonSize);
			button.setHeightPx(buttonSize);
			button.setPaddingPx(buttonPadding);
			button.setBackgroundColor(buttonBackColor);
			pickerAdapter.addItem(button);
		}

		protected abstract int getNumberOfColumns();

		protected abstract void addButtons();

	}

	/**
	 * AsyncTask to handle the Captured Images
	 * 
	 * @author Michalis Vitos
	 * 
	 */
	public class HandleImage extends AsyncTask<Void, Void, Bitmap>
	{
		private ProgressDialog dialog;
		private byte[] data;
		private ImageView reviewView;

		public HandleImage(byte[] data, ImageView reviewView)
		{
			this.data = data;
			this.reviewView = reviewView;
		}

		@Override
		protected void onPreExecute()
		{
			dialog = new ProgressDialog(CameraView.this.getContext());
			dialog.setCancelable(false);
			dialog.show();
		}

		@Override
		protected Bitmap doInBackground(Void... params)
		{
			Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);
			return scaleAndRotate(picture);
		}

		@Override
		protected void onPostExecute(Bitmap picture)
		{
			// Display rotated picture
			reviewView.setImageBitmap(picture);

			// Switch to review mode:
			showNext();
			handlingClick = false;

			cameraController.stopPreview();
			// Close the dialog
			dialog.cancel();
		}

		protected Bitmap scaleAndRotate(Bitmap picture)
		{
			// TODO use EXIF data to determine proper rotation? Cf. http://stackoverflow.com/q/12944123/1084488

			// Find the Aspect Ratio
			Float width = Float.valueOf(picture.getWidth());
			Float height = Float.valueOf(picture.getHeight());
			Float ratio = width / height;

			// Scale
			picture = Bitmap.createScaledBitmap(picture, (int) (PREVIEW_SIZE * ratio), PREVIEW_SIZE, false);

			// Rotate
			Matrix bitmapMatrix = new Matrix();
			bitmapMatrix.postRotate(90);

			return Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), bitmapMatrix, false);
		}
	}

}
