package uk.ac.ucl.excites.collector.geo;

import uk.ac.ucl.excites.storage.types.Orientation;

/**
 * @author mstevens
 *
 */
public interface OrientationListener
{
	
	public void onOrientationChanged(Orientation orientation); 

}