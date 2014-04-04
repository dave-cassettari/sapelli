package uk.ac.ucl.excites.sapelli.collector.ui;

import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.AudioField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ButtonField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.CheckBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.OrientationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LabelField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.MultiListField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Page;
import uk.ac.ucl.excites.sapelli.collector.model.fields.PhotoField;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AudioUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.ButtonUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.CheckBoxUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.ChoiceUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.LabelUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.LocationUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.MultiListUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.OrientationUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.PageUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.PhotoUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.TextBoxUI;

public interface CollectorUI<V, UI extends CollectorUI<V, UI>>
{

	public ChoiceUI<V, UI> createChoiceUI(ChoiceField cf);

	public PhotoUI<V, UI> createPhotoUI(PhotoField pf);

	public AudioUI<V, UI> createAudioUI(AudioField af);

	public LocationUI<V, UI> createLocationUI(LocationField lf);
	
	public OrientationUI<V, UI> createOrientationUI(OrientationField of);
	
	public LabelUI<V, UI> createLabelUI(LabelField lf);
	
	public ButtonUI<V, UI> createButtonUI(ButtonField bf);

	public TextBoxUI<V, UI> createTextFieldUI(TextBoxField tf);
	
	public CheckBoxUI<V, UI> createCheckBoxFieldUI(CheckBoxField cbf);
	
	public MultiListUI<V, UI> createMultiListUI(MultiListField mlf);
	
	public PageUI<V, UI> createPageUI(Page page);

	public void setField(Field currentField);
	
	public FieldUI<?, V, UI> getCurrentFieldUI();
	
	public int getSpacingPx();
	
	public int getScreenWidthPx();
	
	public int getScreenHeightPx();
	
}