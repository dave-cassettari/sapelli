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

package uk.ac.ucl.excites.sapelli.shared.io;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;

import uk.ac.ucl.excites.sapelli.shared.util.BigIntegerUtils;


/**
 * A stream where bits can be written to. Provides write methods for various (primitive) types.<br/>
 * <br/>
 * Heavily modified/extended version of original work by Nayuki Minase:<br/>
 * 		- Source: <a href="https://github.com/nayuki/Huffman-Coding/blob/master/src/nayuki/huffmancoding/BitOutputStream.java">https://github.com/nayuki/Huffman-Coding/blob/master/src/nayuki/huffmancoding/BitOutputStream.java</a><br/>
 * 		- License: MIT License<br/>
 * 
 * @author mstevens
 */
public abstract class BitOutputStream extends OutputStream
{

	//STATIC
	private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
	private static final Charset UTF16BE = Charset.forName("UTF-16BE");
	
	//DYNAMIC
	protected boolean closed;
	protected int numberOfBitsWritten;
	
	public BitOutputStream()
	{
		closed = false;
		numberOfBitsWritten = 0;
	}

	/**
	 * Writes an individual bit (a boolean) to the output
	 * 
	 * @param bit bit (true = 1; false = 0) to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(boolean bit) throws IOException
	{
		if(closed)
			throw new IOException("This stream is closed");
		writeBit(bit);
		numberOfBitsWritten++;
	}
	
	protected abstract void writeBit(boolean bit) throws IOException;
	
	/**
	 * Writes an array series of bits (booleans) to the output
	 * 
	 * @param bits the bits to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(boolean[] bits) throws IOException
	{
		for(boolean bit : bits)
			write(bit);
	}
	
	/**
	 * Writes the bits in a {@link BitArray} to the output
	 * 
	 * @param bits BitArray to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(BitArray bits) throws IOException
	{
		bits.writeTo(this);
	}
	
	/**
	 * Writes a byte to the output
	 * 
	 * Uses MSB 0 bit order (i.e. the most significant bit is written first)
	 * 
	 * @param b byte to be written
	 * @throws IOException if an I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/Bit_numbering">http://en.wikipedia.org/wiki/Bit_numbering</a>
	 */
	public void write(byte b) throws IOException
	{
		for(int i = 7; i >= 0; i--) //MSB first
			write((b & (1 << i)) != 0);
	}
	
	/**
	 * Writes a array of bytes to the output
	 * 
	 * As in OutputStream.write(byte[]), the bytes are written in order (i.e. bytes[0] is written first) 
	 * 
	 * @see java.io.OutputStream#write(byte[])
	 * 
	 * @param bytes bytes to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(byte[] bytes) throws IOException
	{
		write(bytes, 0, bytes.length);
	}

	/**
	 * Writes a sub-array of the provided byte array to the output
	 * 
	 * As in OutputStream.write(byte[],int,int), the bytes are written in order (i.e. bytes[off] is the first byte written and bytes[off+len-1] is the last byte written).
	 * 
	 * @see java.io.OutputStream#write(byte[],int,int)
	 * 
	 * @param bytes byte array from which a sub-array need to be written
	 * @param off offset
	 * @param len number of bytes to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(byte[] bytes, int off, int len) throws IOException
	{
		if(off < 0)
			throw new IllegalArgumentException("Negative offset");
		if(len < 0)
			throw new IllegalArgumentException("Negative length");
		if(off + len > bytes.length)
			throw new ArrayIndexOutOfBoundsException();
		for(int i = 0; i < len; i++)
			write(bytes[off+i]);		
	}

	/**
	 * Writes a (signed, 16bit) short to the output
	 * 
	 * @param value integer value to write to the output
	 * @throws IOException if an I/O error occurs
	 */
	public void write(short value) throws IOException
	{
		write((long) value, Short.SIZE, true/*, ByteOrder.nativeOrder()*/);
	}
	
	/**
	 * Writes a (signed, 32bit) int to the output.
	 * 
	 * Warning: does *not* conform to semantics of {@link java.io.OutputStream#write(int)}.
	 * 
	 * @param value integer value to write to the output
	 * @throws IOException if an I/O error occurs
	 */
	public void write(int value) throws IOException
	{
		write((long) value, Integer.SIZE, true/*, ByteOrder.nativeOrder()*/);
	}
	
	/**
	 * Writes a (signed, 64bit) long to the output
	 * 
	 * @param value integer value to write to the output
	 * @throws IOException if an I/O error occurs
	 */
	public void write(long value) throws IOException
	{
		write(value, Long.SIZE, true/*, ByteOrder.nativeOrder()*/);
	}
	
	/**
	 * Writes an integer number (provided as a long value) of specified number of bits and with specified "signedness" to the output.
	 * 
	 * Uses Two's Complement representation for signed values.
	 * Currently only supports big-endian byte order (and MSB 0 bit numbering), meaning the more significant bits (and bytes) are written first.
	 * 
	 * @param value integer value to write to the output
	 * @param numberOfBits number of bits to use (a size of 0 bits is allowed but only a {@code value} equal to 0 will fit)
	 * @param signed the "signedness" (true = signed; false = unsigned)
	 * @throws IOException if an I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/Integer_(computer_science)">http://en.wikipedia.org/wiki/Integer_(computer_science)</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Two's_complement">http://en.wikipedia.org/wiki/Two's_complement</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Endianness">http://en.wikipedia.org/wiki/Endianness</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Bit_numbering">http://en.wikipedia.org/wiki/Bit_numbering</a>
	 */
	public void write(long value, int numberOfBits, boolean signed/*, ByteOrder order*/) throws IOException
	{
		//TODO add support for little-endian byte order (and perhaps LSB 0 bit numbering)
		write(BigInteger.valueOf(value), numberOfBits, signed);
		/*
		//Version without conversion to BigInteger (works just as well; kept for reference only):
		//Do checks:
		if(numberOfBits < 1)
			throw new IllegalArgumentException("Invalid number of bits (" + numberOfBits + ").");
		if(signed)
		{	//Signed
			if(value < (long) (- Math.pow(2, numberOfBits - 1)) || value > (long) (Math.pow(2, numberOfBits - 1) - 1))
				throw new IllegalArgumentException("Signed value (" + value + ") does not fit in " + numberOfBits + " bits.");
		}
		else
		{	//Unsigned
			if(value < 0l)
				throw new IllegalArgumentException("Cannot write negative value as unsigned integer.");
			if(value > (long) (Math.pow(2, numberOfBits) - 1))
				throw new IllegalArgumentException("Unsigned value (" + value + ") does not fit in " + numberOfBits + " bits.");
		}
		//Write the bits (MSB first):
		for(int i = numberOfBits - 1; i >= 0; i--)
			write((value >> i) % 2l != 0);
		*/
	}
	
	/**
	 * Writes an BigInteger number of specified number of bits and with specified "signedness" to the output.
	 * 
	 * Uses Two's Complement representation for signed values.
	 * Currently only supports big-endian byte order (and MSB 0 bit numbering), meaning the more significant bits (and bytes) are written first.
	 * 
	 * @param value BigInteger value to write to the output
	 * @param numberOfBits number of bits to use (a size of 0 bits is allowed but only a {@code value} equal to 0 will fit)
	 * @param signed the "signedness" (true = signed; false = unsigned)
	 * @throws IOException if an I/O error occurs
	 * @see java.math.BigInteger
	 * @see <a href="http://en.wikipedia.org/wiki/Integer_(computer_science)">http://en.wikipedia.org/wiki/Integer_(computer_science)</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Two's_complement">http://en.wikipedia.org/wiki/Two's_complement</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Endianness">http://en.wikipedia.org/wiki/Endianness</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Bit_numbering">http://en.wikipedia.org/wiki/Bit_numbering</a>
	 */
	public void write(BigInteger value, int numberOfBits, boolean signed/*, ByteOrder order*/) throws IOException
	{
		//TODO add support for little-endian byte order (and perhaps LSB 0 bit numbering)
		//Do checks:
		if(value == null)
			throw new NullPointerException("value cannot be null.");
		if(numberOfBits < 0)
			throw new IllegalArgumentException("numberOfBits (" + numberOfBits + ") cannot be negative!");
		if(!signed && value.signum() == -1)
			throw new IllegalArgumentException("Cannot write negative value (" + value.toString() + ") as unsigned integer.");
		// Compute min/maxValues:
		BigInteger minValue = BigIntegerUtils.GetMinValue(numberOfBits, signed);
		BigInteger maxValue = BigIntegerUtils.GetMaxValue(numberOfBits, signed);
		// Check if value fits:
		if(value.compareTo(minValue) < 0 || value.compareTo(maxValue) > 0)
			throw new IllegalArgumentException((signed ? "S" : "Uns") + "igned value (" + value.toString() + ") does not fit in " + numberOfBits + " bits, values must be in range [" + minValue.toString() + "; " + maxValue.toString() + "] (inclusive).");
		/*Write the bits
		 *	The most significant bit is written first ("MSB 0" bit numbering).
		 *	But we need to count backwards because BigInteger uses "LSB 0" bit numbering internally (i.e. the most significant bit is at address numburOfBits-1). */
		for(int i = numberOfBits - 1; i >= 0; i--)
			write(value.testBit(i));
	}
	
	/**
	 * Writes a (32bit) float to the output
	 * 
	 * @param value to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(float value) throws IOException
	{
		write(Float.floatToRawIntBits(value), Integer.SIZE, true);
	}
	
	/**
	 * Writes a (64bit) double to the output
	 * 
	 * @param value to be written
	 * @throws IOException if an I/O error occurs
	 */
	public void write(double value) throws IOException
	{
		write(Double.doubleToRawLongBits(value), Long.SIZE, true);
	}
		
	/**
	 * Writes a string, encoded using the default charset (UTF-8), to the output
	 * 
	 * @param value string to be written
	 * @return number of bytes written
	 * @throws IOException if an I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/UTF-8">http://en.wikipedia.org/wiki/UTF-8</a>
	 */
	public int write(String value) throws IOException
	{
		return write(value, DEFAULT_CHARSET);
	}
	
	/**
	 * Writes a string, encoded using the provided Charset, to the output
	 * 
	 * @param value string to be written
	 * @param charset the Charset to use to encode the string
	 * @return number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	public int write(String value, Charset charset) throws IOException
	{
		byte[] bytes = value.getBytes(charset);
		write(bytes);
		return bytes.length;
	}
	
	/**
	 * Writes a single (16 bit) char to the output.
	 * Always uses UTF-16BE encoding (for now).
	 * 
	 * @param value char to write
	 * @throws IOException if an I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/UTF-16">http://en.wikipedia.org/wiki/UTF-16</a>
	 */
	public void write(char value) throws IOException
	{
		//TODO support other character encodings?
		write(new String(new char[] { value }).getBytes(UTF16BE));
	}
	
	/**
	 * Closes this stream and the underlying OutputStream.
	 * If called when this bit stream is not at a byte boundary, then the minimum number of zeros (between 0 and 7) are written as padding to reach a byte boundary.
	 * 
	 * @throws IOException if an I/O error occurs
	 * @see java.io.OutputStream#close()
	 */
	public void close() throws IOException
	{
		this.closed = true;
	}
    
    public int getNumberOfBitsWritten()
    {
    	return numberOfBitsWritten;
    }

}
