package uk.ac.ucl.excites.sapelli.collector.model.fields;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.FieldUI;
import uk.ac.ucl.excites.sapelli.shared.util.CollectionUtils;

/**
 * A Field that allows the capture of "drawings" that the user produces through some medium (e.g. a touchscreen). The produced captures are handled in a similar way to photos.
 * @author benelliott
 *
 */
public class DrawingField extends MediaField
{
	static private final String MEDIA_TYPE_PNG = "DRAWING_PNG";
	static private final String EXTENSION_PNG = "png";
	public static final String DEFAULT_BACKGROUND_COLOR = "#BABABA"; // light grey
	public static final String DEFAULT_STROKE_COLOR = "#000000"; // black
	public static final float DEFAULT_STROKE_WIDTH = 20f;

	private String backgroundColor = DEFAULT_BACKGROUND_COLOR;
	private String strokeColor = DEFAULT_STROKE_COLOR;
	private float strokeWidth = DEFAULT_STROKE_WIDTH;
	private String captureButtonImageRelativePath;
	private String backgroundImageRelativePath;

	public DrawingField(Form form, String id, String caption)
	{
		super(form, id, caption);		
	}

	@Override
	public <V, UI extends CollectorUI<V, UI>> FieldUI<? extends Field, V, UI> createUI(UI collectorUI)
	{
		return collectorUI.createDrawingUI(this);
	}
	
	@Override
	public String getMediaType()
	{
		return MEDIA_TYPE_PNG;
	}

	@Override
	protected String getFileExtension(String mediaType)
	{
		return EXTENSION_PNG;
	}

	/**
	 * @return the captureButtonImageRelativePath
	 */
	public String getCaptureButtonImageRelativePath()
	{
		return captureButtonImageRelativePath;
	}

	/**
	 * @param captureButtonImageRelativePath the captureButtonImageRelativePath to set
	 */
	public void setCaptureButtonImageRelativePath(String captureButtonImageRelativePath)
	{
		this.captureButtonImageRelativePath = captureButtonImageRelativePath;
	}
	
	/**
	 * @return the backgroundImageRelativePath
	 */
	public String getBackgroundImageRelativePath()
	{
		return backgroundImageRelativePath;
	}

	/**
	 * @param backgroundImageRelativePath the backgroundImageRelativePath to set
	 */
	public void setBackgroundImageRelativePath(String backgroundImageRelativePath)
	{
		this.backgroundImageRelativePath = backgroundImageRelativePath;
	}

	@Override
	public List<File> getFiles(FileStorageProvider fileStorageProvider)
	{
		List<File> paths = new ArrayList<File>();
		CollectionUtils.addIgnoreNull(paths, fileStorageProvider.getProjectImageFile(form.project, captureButtonImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, fileStorageProvider.getProjectImageFile(form.project, discardButtonImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, fileStorageProvider.getProjectImageFile(form.project, backgroundImageRelativePath));
		return paths;
	}
	
	/**
	 * @return the backgroundColor
	 */
	public String getBackgroundColor()
	{
		return backgroundColor;
	}

	/**
	 * @param backgroundColor the backgroundColor to set
	 */
	public void setBackgroundColor(String backgroundColor)
	{
		this.backgroundColor = backgroundColor;
	}

	/**
	 * @return the strokeColor
	 */
	public String getStrokeColor()
	{
		return strokeColor;
	}

	/**
	 * @param strokeColor the strokeColor to set
	 */
	public void setStrokeColor(String strokeColor)
	{
		this.strokeColor = strokeColor;
	}

	/**
	 * @return the strokeWidth
	 */
	public float getStrokeWidth()
	{
		return strokeWidth;
	}

	/**
	 * @param strokeWidth the strokeWidth to set
	 */
	public void setStrokeWidth(float strokeWidth)
	{
		this.strokeWidth = strokeWidth;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true; // references to same object
		if(obj instanceof DrawingField)
		{
			DrawingField that = (DrawingField) obj;
			return	super.equals(that) && // MediaField#equals(Object)
					(this.captureButtonImageRelativePath != null ? this.captureButtonImageRelativePath.equals(that.captureButtonImageRelativePath) : that.captureButtonImageRelativePath == null) &&
					(this.discardButtonImageRelativePath != null ? this.discardButtonImageRelativePath.equals(that.discardButtonImageRelativePath) : that.discardButtonImageRelativePath == null) &&
					(this.backgroundImageRelativePath != null ? this.backgroundImageRelativePath.equals(that.backgroundImageRelativePath) : that.backgroundImageRelativePath == null) &&
					this.backgroundColor == that.backgroundColor &&
					this.strokeColor == that.strokeColor &&
					this.strokeWidth == that.strokeWidth;
		}
		else
			return false;
	}
	
	@Override
	public int hashCode()
	{
		int hash = super.hashCode(); // MediaField#hashCode()
		hash = 31 * hash + (captureButtonImageRelativePath == null ? 0 : captureButtonImageRelativePath.hashCode());
		hash = 31 * hash + (discardButtonImageRelativePath == null ? 0 : discardButtonImageRelativePath.hashCode());	
		hash = 31 * hash + backgroundColor.hashCode();
		hash = 31 * hash + strokeColor.hashCode();
		hash = 31 * hash + (int)strokeWidth;
		return hash;
	}

}