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

package uk.ac.ucl.excites.sapelli.storage.types;

import java.io.Serializable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author mstevens
 * 
 */
public class TimeStamp implements Comparable<TimeStamp>, Serializable
{

	// STATICS-------------------------------------------------------
	private static final long serialVersionUID = 2L;
	
	static private final int HOUR_MS = 60 /* minutes */* 60 /* seconds */* 1000 /* milliseconds */;
	static private final int QUARTER_OF_AN_HOUR_MS = 15 /* minutes */* 60 /* seconds */* 1000 /* milliseconds */;

	/**
	 * Note (2013-07-13): Implementation used to be: return value.getZone().toTimeZone().getRawOffset(); But that is incorrect during summer time! The current
	 * implementation could be shortened by using only Joda-time's DateTimeZone and no longer also the native TimeZone, like so return
	 * value.getZone().getOffset(value.getMillis()); However that causes weird crashes in Joda-time (probably due to a bug in the library).
	 * 
	 * @param value
	 * @return
	 */
	static public int getTimeZoneOffsetMS(DateTime value)
	{
		return value.getZone().toTimeZone().getOffset(value.getMillis());
	}

	static public int getTimeZoneOffsetQH(DateTime value)
	{
		return getTimeZoneOffsetMS(value) / QUARTER_OF_AN_HOUR_MS;
	}

	/**
	 * Note (2013-07-13): Implementation used to be:
	 * 	return DateTimeZone.forTimeZone(uk.ac.ucl.excites.util.TimeUtils.getTimeZone(quarterHourOffset * QUARTER_OF_AN_HOUR_MS));
	 * Seems to make no difference w.r.t. offset (although we do not get "named" zones this way, but the names could have been wrong anyway, due to DST)
	 * 
	 * @param quarterHourOffset
	 * @return
	 */
	static public DateTimeZone getDateTimeZoneFor(int quarterHourOffset)
	{
		return DateTimeZone.forOffsetMillis(quarterHourOffset * QUARTER_OF_AN_HOUR_MS);
	}

	static public float getTimeZoneOffsetH(DateTime value)
	{
		return ((float) getTimeZoneOffsetMS(value)) / HOUR_MS;
	}
	
	/**
	 * Current time in the current/default timezone
	 * 
	 * @return
	 */
	static public TimeStamp now()
	{
		return new TimeStamp();
	}

	// DYNAMICS------------------------------------------------------
	private final long msSinceEpoch;
	private final int quarterHourOffsetWrtUTC;
	private transient DateTime dateTime; // transient!

	/**
	 * *Now* constructor
	 */
	public TimeStamp()
	{
		this(DateTime.now());
	}
	
	/**
	 * @param msSinceEpoch
	 * @param quarterHourOffsetWrtUTC
	 */
	public TimeStamp(long msSinceEpoch, int quarterHourOffsetWrtUTC)
	{
		this.msSinceEpoch = msSinceEpoch;
		this.quarterHourOffsetWrtUTC = quarterHourOffsetWrtUTC;
	}
	
	/**
	 * @param msSinceEpoch
	 */
	public TimeStamp(long msSinceEpoch)
	{
		this(new DateTime(msSinceEpoch));
	}

	/**
	 * @param dateTime
	 */
	public TimeStamp(DateTime dateTime)
	{
		this(dateTime.getMillis(), getTimeZoneOffsetQH(dateTime));
		this.dateTime = dateTime;
	}
	
	/**
	 * @param msSinceEpoch
	 * @param dateTimeZone
	 */
	public TimeStamp(long msSinceEpoch, DateTimeZone dateTimeZone)
	{
		this(new DateTime(msSinceEpoch, dateTimeZone));
	}
	
	/**
	 * Copy constructor
	 * 
	 * @param timeStamp
	 */
	public TimeStamp(TimeStamp timeStamp)
	{
		this.msSinceEpoch = timeStamp.msSinceEpoch;
		this.quarterHourOffsetWrtUTC = timeStamp.quarterHourOffsetWrtUTC;
		this.dateTime = timeStamp.dateTime;
	}

	/**
	 * @return the msSinceEpoch
	 */
	public long getMsSinceEpoch()
	{
		return msSinceEpoch;
	}

	/**
	 * @return the quarterHourOffsetWrtUTC
	 */
	public int getQuarterHourOffsetWrtUTC()
	{
		return quarterHourOffsetWrtUTC;
	}
	
	/**
	 * @return the offset of the timezone wrt UTC 
	 */
	public float getHourOffsetWrtUTC()
	{
		return quarterHourOffsetWrtUTC / 4.0f;
	}

	public DateTime toDateTime()
	{
		if(dateTime == null)
			dateTime = new DateTime(msSinceEpoch, getDateTimeZoneFor(quarterHourOffsetWrtUTC));
		return dateTime;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj instanceof TimeStamp)
		{
			TimeStamp other = (TimeStamp) obj;
			return this.msSinceEpoch == other.msSinceEpoch && this.quarterHourOffsetWrtUTC == other.quarterHourOffsetWrtUTC;
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		int hash = 1;
		hash = 31 * hash + (int) (msSinceEpoch ^ (msSinceEpoch >>> Integer.SIZE));
		hash = 31 * hash + quarterHourOffsetWrtUTC;
		return hash;
	}

	@Override
	public int compareTo(TimeStamp another)
	{
		if(this == another)
			return 0;
		// Note: we cannot do (this.msSinceEpoch - other.msSinceEpoch) because that can cause overflow
		if(this.msSinceEpoch == another.msSinceEpoch)
			return 0;
		if(this.msSinceEpoch < another.msSinceEpoch)
			return -1;
		else
			return 1;
	}
	
	public boolean isBefore(TimeStamp another)
	{
		return this.msSinceEpoch < another.msSinceEpoch;
	}
	
	public boolean isAfter(TimeStamp another)
	{
		return another.msSinceEpoch < this.msSinceEpoch;
	}
	
	@Override
	public String toString()
	{
		return toDateTime().toString();
	}
	
	public String format(DateTimeFormatter formatter)
	{
		return formatter.print(toDateTime());
	}

}
