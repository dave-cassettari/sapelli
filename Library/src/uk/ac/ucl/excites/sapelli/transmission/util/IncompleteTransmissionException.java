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

package uk.ac.ucl.excites.sapelli.transmission.util;

import uk.ac.ucl.excites.sapelli.transmission.Transmission;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.SMSTransmission;

/**
 * @author mstevens
 *
 */
public class IncompleteTransmissionException extends Exception
{

	private static final long serialVersionUID = 1L;

	private Transmission transmission;
	
	/**
	 * @param transmission
	 */
	public IncompleteTransmissionException(Transmission transmission)
	{
		this(transmission, "Incomplete transmission");
	}
	
	/**
	 * @param transmission
	 */
	public IncompleteTransmissionException(SMSTransmission<?> transmission)
	{
		this(transmission, "Incomplete transmission, " + (transmission.getTotalNumberOfParts() - transmission.getCurrentNumberOfParts()) + "/" + transmission.getTotalNumberOfParts() + " parts missing");
	}

	/**
	 * @param transmission
	 * @param detailMessage
	 */
	public IncompleteTransmissionException(Transmission transmission, String detailMessage)
	{
		super(detailMessage);
		this.transmission = transmission;	
	}
	
	/**
	 * @param transmission
	 * @param detailMessage
	 * @param cause
	 */
	public IncompleteTransmissionException(Transmission transmission, String detailMessage, Throwable cause)
	{
		super(detailMessage, cause);
		this.transmission = transmission;
	}

	/**
	 * @return the transmission
	 */
	public Transmission getTransmission()
	{
		return transmission;
	}

}
