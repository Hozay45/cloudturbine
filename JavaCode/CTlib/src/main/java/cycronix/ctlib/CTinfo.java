/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package cycronix.ctlib;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Utility class for CloudTurbine debugging and file info.
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/05/02
 * 
*/
//--------------------------------------------------------------------------------------------------------

public class CTinfo {

	private static boolean debug = false;
	
	private CTinfo() {};		// constructor
	
	//--------------------------------------------------------------------------------------------------------
	// debug methods
	
	/**
	 * Set debug mode for all CTlib methods.
	 * @param setdebug boolean true/false debug mode
	 */	
	public static void setDebug(boolean setdebug) {
		debug = setdebug;
		if(debug) debugPrint("debug set true!");
	}
	
	private static String callerClassName() {
		String cname = new Exception().getStackTrace()[2].getClassName();		// 2 levels up
		cname = cname.substring(cname.lastIndexOf(".")+1);	// last part
		return cname;
	}
	
	/**
	 * Print given debug message if setDebug mode true. Automatically prepends calling class name to debug print.
	 * @param idebug over-ride (force) debug print true/false
	 * @param msg debug message
	 * @see #debugPrint(String)
	 */
	public static void debugPrint(boolean idebug, String msg) {
		if(debug || idebug) System.err.println(callerClassName()+": "+msg);
	}
	
	/**
	 * Print given debug message if setDebug mode true. Automatically prepends calling class name to debug print.
	 * @param msg debug message 
	 * @see #debugPrint(boolean, String)
	 */
	public static void debugPrint(String msg) {
		if(debug) System.err.println(callerClassName()+": "+msg);
	}
	
	/**
	 * Print given warning message. Automatically prepends calling class name to debug print.
	 * @param msg warning message 
	 */
	public static void warnPrint(String msg) {
		System.err.println(callerClassName()+": "+msg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	// fileType:  return file type code based on file extension
	
	/**
	 * Return file type character code based on file suffix
	 * @param fname file name
	 * @return filetype character code
	 * @see #fileType(String,char)
	 */
	public static char fileType(String fname) {
		return fileType(fname, 'N');			// was 'n'
	}
	
	/**
	 * Return file type character code based on file suffix
	 * @param fName file name
	 * @param typeDefault default type if no built-in rule found
	 * @return filetype code
	 * @see #fileType(String)
	 */
	public static char fileType(String fName, char typeDefault) {
		
		char fType = typeDefault;		// default
		if		(fName.endsWith(".bin")) fType = 'B';
		else if	(fName.endsWith(".jpg")) fType = 'B';
		else if	(fName.endsWith(".JPG")) fType = 'B';
		else if	(fName.endsWith(".wav")) fType = 'j';		// was 'B'
		else if	(fName.endsWith(".pcm")) fType = 'j';		// was 'B'
		else if	(fName.endsWith(".mp3")) fType = 'B';
		else if	(fName.endsWith(".pcm")) fType = 'j';		// FFMPEG s16sle audio
		else if	(fName.endsWith(".txt")) fType = 's';	
		else if	(fName.endsWith(".f32")) fType = 'f';
		else if	(fName.endsWith(".f64")) fType = 'F';
		else if	(fName.endsWith(".i16")) fType = 'j';		// 's' is string for compat with WebTurbine
		else if	(fName.endsWith(".i32")) fType = 'i';
		else if	(fName.endsWith(".i64")) fType = 'I';
		else if (fName.endsWith(".Num")) fType = 'N';
		else if (fName.endsWith(".num")) fType = 'n';
		else if (fName.endsWith(".csv")) fType = 'N';		// default
		return(fType);
	}
	
	/**
	 * Data word size corresponding to given file type
	 * @param ftype filetype character code
	 * @return word size (bytes per word)
	 * @see #fileType(String)
	 */
	public static int wordSize(char ftype) {
		switch(ftype) {
		case 'f':	return 4;
		case 'F':	return 8;
		case 'j':	return 2;
		case 'i':	return 4;
		case 'I':	return 8;
		case 'N':	return 8;
		case 'n':	return 4;
		default:	return 1;		// wordsize=1, implies indivisible (non-packed) data
		}
	}
	
	public static int wordSize(String fname) {
		return wordSize(fileType(fname)); 
	}
	
	// return data use under source folder.  
	public static long dataUsage(String folder) {
		return diskUsage(folder, 1);
	}
	
	// return (estimated) disk use under source folder.  Requires disk blocksize (not available via Java directly)
	public static long diskSize=0;		// for fast retrieval after diskUsage call (cluge, need sourceStats class or equiv)
	public static long diskUsage(String folder, int bSize) {
		Path path = Paths.get(folder);
		diskSize=0;
		
		/* 		// Java8 allows simpler version (but build for Java7 for AndroidStudio compat):

		long size = 0;
		try {
			size = Files.walk(path).mapToLong( p -> p.toFile().length() ).sum();
		} catch (IOException e) { System.err.println("dataSize IOException: "+e); }

		return size;
		 */		

		final long blockSize = bSize;
		final AtomicLong size = new AtomicLong(0);
		try
		{
			Files.walkFileTree (path, new SimpleFileVisitor<Path>() 
			{
				@Override public FileVisitResult 
				visitFile(Path file, BasicFileAttributes attrs) {
					long dsize = attrs.size();
					diskSize += dsize;
					if(blockSize > 1)
						dsize =  blockSize * (long)(Math.ceil((double)dsize / (double)blockSize));
					size.addAndGet (dsize);
					
					return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult 
				visitFileFailed(Path file, IOException exc) {

					System.out.println("diskUsage, skipped: " + file.toFile().getAbsolutePath() + " (" + exc + ")");
					// Skip folders that can't be traversed
					return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult
				postVisitDirectory (Path dir, IOException exc) {

					if (exc != null)
						System.out.println("diskUsage, had trouble traversing: " + dir + " (" + exc + ")");
					// Ignore errors traversing a folder
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException e)
		{
			throw new AssertionError ("walkFileTree will not throw IOException if the FileVisitor does not");
		}

		return size.get();
	}
	
  	/**
  	 * Parse file-time given string 
  	 * @param fname name of file to parse
  	 * @return double time in seconds
  	 */
  	
  	public static double fileTime(String fname) {

  		if(fname.endsWith(".zip")) fname = fname.substring(0,fname.length()-4);		// strip (only) trailing ".zip"
    	
		// new multi-part timestamp logic:  parse path up from file, sum relative times until first absolute fulltime
		String[] pathparts = fname.split(Pattern.quote(File.separator)+"|/");		// use either forward or backward slash (forward is used *inside* zip files)
		Long sumtime = 0L;
		double ftime = 0.;

//  		System.err.println("fileTime of: "+fname+", File.separator: "+File.separator);

		for(int i=pathparts.length-1; i>=0; i--) {		// parse right to left
			String thispart = pathparts[i];
			Long thistime = 0L;
			try {
				thistime = Long.parseLong(thispart);
				sumtime += thistime;							// presume consistent msec or sec times all levels
			} catch(NumberFormatException e) {
				continue;		// keep looking?
			}
			
			if(thistime >= 1000000000000L) {	// absolute msec (deprecated).  absolute sec not used nor reliable.
				ftime = (double)sumtime / 1000.;
//				System.err.println("******msec fileTime: "+ftime);
				return ftime;
			}
			
//			System.err.println("***fileTime fname: "+fname+", thispart: "+thispart+", thistime: "+thistime+", sumtime: "+sumtime);
		}
		
		if(sumtime >= 1000000000000L) {			// relative msec		
			ftime = (double)sumtime / 1000.;
//			System.err.println("******msec fileTime: "+ftime);
			return ftime;
		}
		else {									// relative sec
			//			if(thistime >= 1000000000L) {		
			ftime = (double)sumtime;
//			System.err.println("******sec fileTime: "+ftime);
			return ftime;
		}


//		return 0.;		// not a problem if a non-timestamp (e.g. channel) folder
  	}
}
