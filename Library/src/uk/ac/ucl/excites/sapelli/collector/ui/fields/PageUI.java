/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.control.Controller.FormSession.Mode;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorRecord;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Page;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.FieldUI;
import uk.ac.ucl.excites.sapelli.collector.ui.NonSelfLeavingFieldUI;
import uk.ac.ucl.excites.sapelli.shared.util.CollectionUtils;

/**
 * @author mstevens
 *
 */
public abstract class PageUI<V, UI extends CollectorUI<V, UI>> extends NonSelfLeavingFieldUI<Page, V, UI>
{

	protected List<FieldUI<?, V, UI>> fieldUIs;
	
	public PageUI(Page page, Controller controller, UI collectorUI)
	{
		super(page, controller, collectorUI);
		fieldUIs = new ArrayList<FieldUI<?, V, UI>>();
		
		for(Field f : page.getFields())
			CollectionUtils.addIgnoreNull(fieldUIs, f.createUI(collectorUI));
	}
	
	@Override
	public void cancel()
	{
		for(FieldUI<?, V, UI> fUI : fieldUIs)
			fUI.cancel();
	}
	
	@Override
	public boolean leave(CollectorRecord record, boolean noValidation)
	{
		if(noValidation || isValid(record))
		{
			for(FieldUI<?, V, UI> fUI : fieldUIs)
				if(	(controller.getCurrentFormMode() == Mode.CREATE && fUI.getField().isShowOnCreate()) ||
					(controller.getCurrentFormMode() == Mode.EDIT && fUI.getField().isShowOnEdit()))
					fUI.leave(record, true); // skip validation (otherwise we'd repeat it), this means that NonSelfLeavingFieldUIs (and Boolean-column Buttons) will only store their value
			
			// Page will be left (and not to go to one of its contained fields), so disable its triggers:
			controller.disableTriggers(field.getTriggers());
			
			return true;
		}
		return false;
	}

	@Override
	public boolean isValid(CollectorRecord record)
	{
		boolean valid = true;
		for(FieldUI<?, V, UI> fUI : fieldUIs)
		{
			if(	((controller.getCurrentFormMode() == Mode.CREATE && fUI.getField().isShowOnCreate()) ||
				 (controller.getCurrentFormMode() == Mode.EDIT && fUI.getField().isShowOnEdit()))
				&& !isValid(fUI, record))
				valid = false;
		}
		return valid;
	}
	
	public boolean isValid(FieldUI<?, V, UI> fUI, CollectorRecord record)
	{
		boolean valid = fUI.isValid(record);
		markValidity(fUI, valid); // highlight with red border if invalid, remove border (if it is there) if valid
		return valid; 
	}
	
	protected abstract void markValidity(FieldUI<?, V, UI> fieldUI, boolean valid);

	public void clearInvalidity(FieldUI<?, V, UI> fieldUI)
	{
		if(fieldUIs.contains(fieldUI))
			markValidity(fieldUI, true);
	}
	
	@Override
	protected void storeValue(CollectorRecord record)
	{
		// does nothing (Pages have no column of their own)
	}

}