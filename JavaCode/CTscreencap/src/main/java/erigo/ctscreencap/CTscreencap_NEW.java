/*
Copyright 2017 Erigo Technologies LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package erigo.ctscreencap;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import cycronix.ctlib.CTwriter;
import cycronix.ctlib.CTinfo;

/**
 * 
 * Periodically perform a screen capture and save it to CloudTurbine as an image.
 * Optionally, capture audio and save it to CloudTurbine as a series of ".wav" files.
 * 
 * The following classes are involved in this application:
 * 1. CTscreencap: main method; the constructor manages the flow of the whole program
 * 2. DefineCaptureRegion: NO LONGER USED; we previously used this class to allow the
 *       user to select the region of the screen they wish to capture; this is now
 *       done dynamically by the user moving and resizing the main application frame
 * 3. ScreencapTask: generates a screen capture
 * 4. AudiocapTask: capture and save audio to CloudTurbine as ".wav" files
 * 5. Utility: contains utility methods
 * 
 * The CTscreencap constructor is the main work-horse and "manager" for the application.
 * It does the following:
 * 1. Setup a shutdown hook to capture Ctrl+c to cleanly shut down the program
 * 2. Parse command line arguments
 * 3. (NO LONGER USED: Have user specify the region of the screen they wish to capture)
 * 4. Setup a periodic timer to call CTscreencap.run(); each time this
 *    timer expires, it creates an instance of ScreencapTask to generate
 *    a screen capture and put it on the blocking queue.
 * 5. At the bottom of this constructor is a while() loop which
 *    grabs screen capture byte arrays off the blocking queue and send
 *    them to CT.
 * 
 * The JFrame that pops up contains a semi-translucent window (i.e., opacity less than 1.0) which
 * specifies the image capture region.  The Java 1.8 Javadoc for java.awt.Frame.setOpacity()
 * specifies:
 * 
 *  *  The following conditions must be met in order to set the opacity value less than 1.0f:
 *  *
 *  *      The TRANSLUCENT translucency must be supported by the underlying system
 *  *      The window must be undecorated (see setUndecorated(boolean) and Dialog.setUndecorated(boolean))
 *  *      The window must not be in full-screen mode (see GraphicsDevice.setFullScreenWindow(Window)) 
 *  *
 *  *  If the requested opacity value is less than 1.0f, and any of the above conditions are not met, the window opacity will not change, and the IllegalComponentStateException will be thrown. 
 * 
 * We see this behavior immediately upon CTscreencap startup under Mac OS if the JFrame is "decorated"
 * (an IllegalComponentStateException exception is thrown right away).  Surprisingly, for some unknown
 * reason, the transparency works with a decorated JFrame under Windows OS - however, we don't want
 * to count on that.  To be Java compliant, we won't use a decorated window.  This creates the challenge
 * of how do we support moving and resizing the window?  We do this by creating our own simple
 * window manager: CTscreencap implements MouseMotionListener by defining mouseDragged() and
 * mouseMoved().
 * 
 * @author John P. Wilson
 * @version 01/30/2017
 *
 */
public class CTscreencap extends TimerTask implements ActionListener,MouseMotionListener {
	
	// Encoded byte array of the cursor image data from "Cursor_NoShadow.png"
	public static String encoded_cursor_noshadow = "iVBORw0KGgoAAAANSUhEUgAAABQAAAAgCAYAAAASYli2AAACa0lEQVR42q2WT2jSYRjHR+QtwX/oREVBhIYxGzJNyEOIXTp0yr8HYTRQlNz8O6dgoJ4FT2ZeJAw8CEkdhBYMFHfypLBDsZ3CUYcijErn7+l9BCPZbJrvF97L+/D7/N7neZ8/74rVan27QlngdDpfUQWKxWJwuVwvqQG73S6srq7C1tbWMyrAwWAAnU4HhEIhbG9vZ6kAUe12GwQCAbjd7jQVIOro6Ah4PB7j9XpjVICow8ND4HA4jM/ne0IFiKrX68DlcpmdnZ3HVICoWq02dn93d9dBBYiqVCrA5/NHoVDoIRUgqlQqYUoh9D4VICqfz2NFnUej0btUgKhsNgsymWy4t7e3SQWIymQyoFAofsVisVtUgKh4PA4qlepHIpFQUQEyDAOBQADW1ta+E7hsaeAESmoe1tfXvxH3RUsDUaPRCPsoaLXaL8lkkrcQ8OTkBFqt1oXVaDRAo9GAXq//TKA35gaSmgY2m81g3GYti8Xybm7g8fHxuK7JKTj/ldgHBwdweno6tWcymXBMPF8YWK1WgcVigd/vv7CvVqv7CwHL5TJ2F4bMlhzph9Dv9//YhsMhSKVSjKdrLmCxWMSZMiJJ+wgNBoPhU6FQmDplKpUCs9n8/kpgLpcDkUh0HgwGH0wMHo/nKaYEJvFEvV4PbxvLTzkTmE6nQSKRDMPh8L2/DeRGr2N3aTabU6e02Wxgt9tfzwTK5fJBJBK5c5mRfPjG4XBMxZEML1AqlT8vpaFhf3//9qy/YUdBF8/OzsZze2NjA9fXmd2bFPbNq9KAXMIHnU43noKkdl+QUFxb6mmBaWI0Gj/+y5OJfgOMmgOC3DbusQAAAABJRU5ErkJggg==";
	
	public final static double DEFAULT_FPS = 5.0;         // default frames/sec
	public final static double AUTO_FLUSH_DEFAULT = 1.0;  // default auto-flush in seconds
	
	public long capturePeriodMillis;			// period between screen captures (msec)
	public String outputFolder = ".";			// location of output files
	public String sourceName = "CTscreencap";	// output source name
	public String channelName = "image.jpg";	// output channel name
	public boolean bZipMode = true;				// output ZIP files?
	public long autoFlushMillis;				// flush interval (msec)
	public boolean bDebugMode = false;			// run CT in debug mode?
	public boolean bShutdown = false;			// is it time to shut down?
	public boolean bIncludeMouseCursor = true;	// include the mouse cursor in the screencap image?
	public BufferedImage cursor_img = null;		// cursor to add to the screen captures
	public CTwriter ctw = null;					// CloudTurbine writer object
	public Timer timerObj = null;				// Timer object
	public float imageQuality = 0.70f;			// Image quality; 0.00 - 1.00; higher numbers correlate to better quality/less compression
	public Rectangle regionToCapture = null;	// The region to capture
	public BlockingQueue<byte[]> queue = null; 	// Queue of byte arrays containing screen captures to be sent to CT
	public boolean bChangeDetect = false;		// detect and record only images that change (more CPU, less storage)
	public boolean bAudioCapture = false;		// record synchronous audio?
	public boolean bFullScreen = false;			// automatically capture the full screen?
	
	private AudiocapTask audioTask = null;		// audio-capture task (optional)
	
	private JFrame guiFrame = null;				// JFrame which contains translucent panel which defines the capture region
	private JPanel controlsPanel = null;		// Panel which contains UI controls
	private JPanel capturePanel = null;			// Translucent panel which defines the region to capture
	
	// Since translucent panels can only be contained within undecorated Frames (see comments in header above)
	// and since undecorated Frames don't support moving/resizing, we implement our own basic "window manager"
	// by catching mouse move and drag events (see mouseDragged() and mouseMoved() methods below).  The following
	// members are used in this implementation.
	private final static int NO_COMMAND			= 0;
	private final static int MOVE_FRAME			= 1;
	private final static int RESIZE_FRAME_NW	= 2;
	private final static int RESIZE_FRAME_N		= 3;
	private final static int RESIZE_FRAME_NE	= 4;
	private final static int RESIZE_FRAME_E		= 5;
	private final static int RESIZE_FRAME_SE	= 6;
	private final static int RESIZE_FRAME_S		= 7;
	private final static int RESIZE_FRAME_SW	= 8;
	private final static int RESIZE_FRAME_W		= 9;
	private int mouseCommandMode = NO_COMMAND;
	private Point mouseStartingPoint = null;
	private Rectangle frameStartingBounds = null;
	
	//
	// main() function; create an instance of CTscreencap
	//
	public static void main(String[] argsI) {
		new CTscreencap(argsI);
	}
	
	//
	// Constructor and main work-horse for the program.
	// See documentation in the header above for all the things that are done in this method.
	//
	public CTscreencap(String[] argsI) {
		
		//
		// Specify a shutdown hook to catch Ctrl+c
		//
		final CTscreencap temporaryCTS = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
        	@Override
            public void run() {
        		temporaryCTS.exit(true);
            }
        });
		
		//
		// Parse command line arguments
		//
		// 1. Setup command line options
		//
		Options options = new Options();
		// Boolean options (only the flag, no argument)
		options.addOption("h", "help", false, "Print this message");
		options.addOption("nm", "no_mouse_cursor", false, "don't include mouse cursor in output screen capture images");
		options.addOption("nz", "no_zipfiles", false, "don't use ZIP");
		options.addOption("x", "debug", false, "use debug mode");
		options.addOption("cd", "change_detect", false, "detect and record only changed images (default="+bChangeDetect+")"); // MJM
		options.addOption("a", "audio_cap", false, "record audio (default="+bAudioCapture+")"); // MJM
		options.addOption("fs", "full_screen", false, "automatically capture full screen (default="+bFullScreen+")");

		// Command line options that include a flag
		// For example, the following will be for "-outputfolder <folder>   (Location of output files...)"
		Option outputFolderOption = Option.builder("outputfolder")
                .argName("folder")
                .hasArg()
                .desc("Location of output files (source is created under this folder); default = \"" + outputFolder + "\"")
                .build();
		options.addOption(outputFolderOption);
		Option filesPerSecOption = Option.builder("fps")
                .argName("framespersec")
                .hasArg()
                .desc("Desired frame rate (frames/sec); default = " + DEFAULT_FPS)
                .build();
		options.addOption(filesPerSecOption);
		Option autoFlushOption = Option.builder("f")
				.argName("autoFlush")
				.hasArg()
				.desc("flush interval (sec); amount of data per zipfile; default = " + Double.toString(AUTO_FLUSH_DEFAULT))
				.build();
		options.addOption(autoFlushOption);
		Option sourceNameOption = Option.builder("s")
				.argName("source name")
				.hasArg()
				.desc("name of output source; default = \"" + sourceName + "\"")
				.build();
		options.addOption(sourceNameOption);
		Option chanNameOption = Option.builder("c")
				.argName("channelname")
				.hasArg()
				.desc("name of output channel; default = \"" + channelName + "\"")
				.build();
		options.addOption(chanNameOption);
		Option imgQualityOption = Option.builder("q")
				.argName("imagequality")
				.hasArg()
				.desc("image quality, 0.00 - 1.00 (higher numbers are better quality/less compression); default = " + Float.toString(imageQuality))
				.build();
		options.addOption(imgQualityOption);
		
		//
		// 2. Parse command line options
		//
	    CommandLineParser parser = new DefaultParser();
	    CommandLine line = null;
	    try {
	        line = parser.parse( options, argsI );
	    }
	    catch( ParseException exp ) {
	        // oops, something went wrong
	        System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
	     	return;
	    }
	    
	    //
	    // 3. Retrieve the command line values
	    //
	    if (line.hasOption("help")) {
	    	// Display help message and quit
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp( "CTscreencap", options );
	    	return;
	    }
	    // Source name
	    sourceName = line.getOptionValue("s",sourceName);
	    // Channel name
	    channelName = line.getOptionValue("c",channelName);
	    // Auto-flush time
		double autoFlush = Double.parseDouble(line.getOptionValue("f",""+AUTO_FLUSH_DEFAULT));
		autoFlushMillis = (long)(autoFlush*1000.);
	    // ZIP output files?
	    bZipMode = !line.hasOption("no_zipfiles");
	    // Include cursor in output screen capture images?
	    bIncludeMouseCursor = !line.hasOption("no_mouse_cursor");
	    // Where to write the files to
	    outputFolder = line.getOptionValue("outputfolder",outputFolder);
	    // Make sure outputFolder ends in a file separator
	    if (!outputFolder.endsWith(File.separator)) {
	    	outputFolder = outputFolder + File.separator;
	    }
	    // How many frames (i.e., screen dumps) to capture per second
	    double framesPerSec = 0.0;
	    try {
	    	framesPerSec = Double.parseDouble(line.getOptionValue("fps",""+DEFAULT_FPS));
	    	if (framesPerSec <= 0.0) {
	    		throw new NumberFormatException("value must be greater than 0.0");
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nError parsing \"fps\" (it should be a floating point value):\n" + nfe);
	    	return;
	    }
	    capturePeriodMillis = (long)(1000.0 / framesPerSec);
	    // Run CT in debug mode?
	    bDebugMode = line.hasOption("debug");
	    // changeDetect mode? MJM
	    bChangeDetect = line.hasOption("change_detect");
	    // audio capture mode? MJM
	    bAudioCapture = line.hasOption("audio_cap");
	    // Capture the full screen?
	    bFullScreen = line.hasOption("full_screen");
	    // Image quality
	    String imageQualityStr = line.getOptionValue("q",""+imageQuality);
	    try {
	    	imageQuality = Float.parseFloat(imageQualityStr);
	    	if ( (imageQuality < 0.0f) || (imageQuality > 1.0f) ) {
	    		throw new NumberFormatException("");
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nimage quality must be a number in the range 0.0 <= x <= 1.0");
	    	return;
	    }
	    
	    //
	    // THIS IS OLDER CODE WHERE THE USER SPECIFIED THE REGION TO CAPTURE AHEAD OF TIME;
	    // WE NOW DO THIS DYNAMICALLY: THE USER PUTS A FRAME AROUND THE REGION TO CAPTURE
	    // AND CAN CHANGE IT AS THE PROGRAM RUNS
	    //
	    // Determine if the GraphicsDevice supports translucency.
	    // This code is from https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html
	    //
	    // If translucent windows aren't supported, capture the entire screen.
	    // Otherwise, have the user draw a rectangle around the area they wish to capture.
	    //
	    /*
        GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice graphDev = graphEnv.getDefaultScreenDevice();
        if (!graphDev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            System.err.println("Translucency is not supported; capturing the entire screen.");
            regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        } else {
        	// Have user specify region to capture by drawing a rectangle with the mouse
    	    // Create the GUI on the event-dispatching thread
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                	DefineCaptureRegion dcr = new DefineCaptureRegion(temporaryCTS);
                    // Display the window; since this is a modal dialog, this method won't
                	// return until the dialog is hidden
                    dcr.setVisible(true);
                }
            });
        }
        // Wait until user has specified the region to capture before proceeding.
        // (This is a bit hokey just waiting until regionToCapture has been defined, but it works.)
        while (regionToCapture == null) {
        	try {
        		Thread.sleep(500);
        	} catch (Exception e) {
        		// Nothing to do
        	}
        }
        */
	    
	    //
	    // Capture the entire screen in one of two situations:
	    // 1. The user has requested to do this via the -f command line option
	    // 2. If the GraphicsDevice does not support translucency
	    //       (see https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html)
	    // Otherwise, pop up a frame where the user dynamically specifies what to capture.
	    //
	    if (bFullScreen) {
	    	regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	    	System.err.println("Capturing the entire screen; click Ctrl+c when finished\n");
	    } else {
	    	GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
	        GraphicsDevice graphDev = graphEnv.getDefaultScreenDevice();
	        if (!graphDev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
	        	bFullScreen = true;
	            System.err.println("Translucency is not supported; capturing the entire screen; click Ctrl+c when finished\n.");
	            regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	        } else {
	        	// For thread safety: Schedule a job for the event-dispatching thread to create and show the GUI
	        	SwingUtilities.invokeLater(new Runnable() {
	        	    public void run() {
	        	    	temporaryCTS.createAndShowGUI();
	        	    }
	        	});
	        }
	    }
        
		// Setup CTwriter
		try {
			CTinfo.setDebug(bDebugMode);
			ctw = new CTwriter(outputFolder + sourceName);
			ctw.autoFlush(autoFlushMillis);
			ctw.setZipMode(bZipMode);
			ctw.autoSegment(0);
		} catch (IOException ioe) {
			System.err.println("Error trying to create CloudTurbine writer object:\n" + ioe);
			return;
		}
		
		// Setup the asynchronous event queue to store by arrays
		queue = new LinkedBlockingQueue<byte[]>();
		
		// Decode the String corresponding to binary cursor data; produce a BufferedImage with it
		Base64.Decoder byte_decoder = Base64.getDecoder();
		byte[] decoded_cursor_bytes = byte_decoder.decode(encoded_cursor_noshadow);
		try {
			cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
		} catch (IOException ioe) {
			System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
			return;
		}
		
		//
		// Setup periodic screen captures
		//
		// A periodic timer repeatedly calls the run() method in this instance of CTscreencap;
		// this run() method creates a new instance of ScreencapTask and spawns a new Thread
		// to run it.  ScreencapTask.run() generates a screencapture and stores it in the
		// LinkedBlockingQueue managed by CTscreencap.  CTscreencap is executing a continual
		// while() loop to grab the next screencap and send it to CT (see while loop below).
		//
		timerObj = new Timer();
        timerObj.schedule(this, 0, capturePeriodMillis);
        
        //
        // Start audio capture (if requested by the user)
        //
        if (bAudioCapture) {
        	// start audio capture (MJM)
        	// String audioFolder = outputFolder + "CTaudio";
        	String audioFolder = outputFolder + sourceName + "_audio";
        	audioTask = new AudiocapTask(audioFolder);
        }
        
        //
        // While loop to grab screen capture byte arrays off the blocking queue and send them to CT
        //
        int numScreenCaps = 0;
        while (true) {
        	if (bShutdown) {
    			return;
    		}
        	byte[] jpegByteArray = null;
        	try {
				jpegByteArray = queue.take();
			} catch (InterruptedException e) {
				System.err.println("Caught exception working with the LinkedBlockingQueue:\n" + e);
			}
        	if (jpegByteArray != null) {
        		try {
					ctw.putData(channelName,jpegByteArray);
				} catch (Exception e) {
					if (!bShutdown) {
					    System.err.println("Caught CTwriter exception:\n" + e);
					}
				}
        		System.out.print("x");
        		numScreenCaps += 1;
        		if ((numScreenCaps % 20) == 0) {
        			System.err.print("\n");
        		}
        	}
        }
		
	}
	
	//
	// Method called by the periodic timer; spawn a new thread to perform the screen capture.
	//
	// Possibly using a thread pool would save some thread management overhead?
	//
	public void run() {
		if (bShutdown) {
			return;
		}
		if (!bFullScreen) {
			// User will specify the region to capture via the JFame
			// If the JFrame is not yet up, just return
			if ( (guiFrame == null) || (!guiFrame.isShowing()) ) {
				return;
			}
			// Update capture region (this is used by ScreencapTask)
			Point loc = capturePanel.getLocationOnScreen();
			Dimension dim = capturePanel.getSize();
			if ( (dim.width <= 0) || (dim.height <= 0) ) {
				// Don't try to do a screen capture with a non-existent capture area
				return;
			}
			Rectangle tempRegionToCapture = new Rectangle(loc,dim);
			regionToCapture = tempRegionToCapture;
		}
		// Create a new ScreencapTask and run it in a new thread
		ScreencapTask screencapTask = new ScreencapTask(this);
        Thread threadObj = new Thread(screencapTask);
        threadObj.start();
	}
	
	//
	// Pop up the GUI
	// this method should be run in the event-dispatching thread
	//
	private void createAndShowGUI() {
		
		// Make sure we have nice window decorations.
		// JFrame.setDefaultLookAndFeelDecorated(true);
		
		// Create the GUI components
		GridBagLayout framegbl = new GridBagLayout();
		guiFrame = new JFrame("CTscreencap");
		
		// To support a semi-transparent frame, the window must be undecorated
		// See notes in the class header up above about this; also see
		// http://alvinalexander.com/source-code/java/how-create-transparenttranslucent-java-jframe-mac-os-x
		// Here's another way to set semi-transparency
		// guiFrame.getRootPane().putClientProperty("Window.alpha", new Float(0.2f));
        guiFrame.setUndecorated(true);
        
        guiFrame.addMouseMotionListener(this);
		
		guiFrame.setBackground(new Color(0,0,0,0));
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		// guiPanel.setBackground(new Color(0,0,0,0));
		guiPanel.setBackground(Color.RED);
		guiFrame.setFont(new Font("Dialog", Font.PLAIN, 12));
		guiPanel.setFont(new Font("Dialog", Font.PLAIN, 12));
		GridBagLayout controlsgbl = new GridBagLayout();
		controlsPanel = new JPanel(controlsgbl);
		controlsPanel.setBackground(new Color(211,211,211,255));
		capturePanel = new JPanel();
		capturePanel.setBackground(new Color(0,0,0,16));
		capturePanel.setPreferredSize(new Dimension(500,400));
        guiFrame.setAlwaysOnTop(true);
        
		int row = 0;
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		
		// First row: the controls panel
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		Utility.add(guiPanel, controlsPanel, gbl, gbc, 0, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;
		
		// Add controls to the controls panel
		JLabel label1 = new JLabel("Image quality",SwingConstants.LEFT);
		JLabel label2 = new JLabel("Frame rate",SwingConstants.LEFT);
		JButton exitButton = new JButton("Exit");
		exitButton.addActionListener(this);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		gbc.insets = new Insets(10, 10, 0, 10);
		Utility.add(controlsPanel, label1, controlsgbl, gbc, 0, 0, 1, 1);
		gbc.insets = new Insets(10, 10, 10, 10);
		Utility.add(controlsPanel, label2, controlsgbl, gbc, 0, 1, 1, 1);
		gbc.insets = new Insets(10, 0, 10, 10);
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(controlsPanel, exitButton, controlsgbl, gbc, 1, 0, 1, 2);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		
		// Second row: the translucent panel
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, capturePanel, gbl, gbc, 0, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;
		
		// Add guiPanel to guiFrame
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(guiFrame, guiPanel, framegbl, gbc, 0, 0, 1, 1);
		
		// Add menu
		JMenuBar menuBar = createMenu();
		guiFrame.setJMenuBar(menuBar);
		
		guiFrame.addComponentListener(new ComponentAdapter() {
            // The region defined by the capturePanel will be "hollowed out"
			// so that the user can reach through guiFrame and interact
			// with applications which are behind it.
            // If the window is resized, the shape is recalculated here.
            @Override
            public void componentResized(ComponentEvent e) {
            	// capturePanel.getX()
            	// int capturePanelWidth = capturePanel.getWidth();
            	// int capturePanelHeight = capturePanel.getHeight();
            	// Create a rectangle to cover the entire guiFrame
            	Area guiShape = new Area(new Rectangle(0, 0, guiFrame.getWidth(), guiFrame.getHeight()));
            	// Create another rectangle to define the hollowed out region of capturePanel
                guiShape.subtract(new Area(new Rectangle(capturePanel.getX(), capturePanel.getY()+23, capturePanel.getWidth(), capturePanel.getHeight())));
                guiFrame.setShape(guiShape);
            }
        });
		
		// Display the window.
		guiFrame.pack();
		
		guiFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		guiFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit(false);
			}
		});
		
		guiFrame.setVisible(true);
		
	}
	
	//
	// Create menu for the GUI
	//
	private JMenuBar createMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu menu = new JMenu("File");
		menuBar.add(menu);
		JMenuItem menuItem = new JMenuItem("Settings...");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		menuItem = new JMenuItem("Exit");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		return menuBar;
	}
	
	//
	// Callback for UI controls and menu items
	//
	public void actionPerformed(ActionEvent eventI) {
		Object source = eventI.getSource();

		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Exit")) {
			exit(false);
		}
	}
	
	//
	// Exit the application
	//
	private void exit(boolean bCalledFromShutdownHookI) {
		// Flag that it is time to shut down
    	bShutdown = true;
		// Shut down CTwriter
		if (ctw == null) {
			return;
		}
		ctw.close();
		ctw = null;
		// shut down audio
		if(audioTask != null) audioTask.shutDown();
		// Sleep for a bit to allow any currently running tasks to finish
		try {
    		Thread.sleep(1000);
    	} catch (Exception e) {
    		// Nothing to do
    	}
		System.err.println("\nExit CTscreencap\n");
		// If we *are* called from the shutdown hook, don't exit
		// (Java support for the shutdown hook must include its own
		//  code to call exit and if we call exit here, that gets
		//  screwed up.)
		if (!bCalledFromShutdownHookI) {
			System.exit(0);
		}
	}
	
	//
	// Utility function which reads the specified image file and returns
	// a String corresponding to the encoded bytes of this image.
	//
	// Sample code for calling this method to generate an encoded string representation of a cursor image:
	// String cursorStr = null;
	// try {
	//     cursorStr = getEncodedImageString("cursor.png","png");
	//     System.err.println("Cursor string:\n" + cursorStr + "\n");
	// } catch (IOException ioe) {
	//     System.err.println("Caught exception generating encoded string for cursor data:\n" + ioe);
	// }
	//
	// A buffered image can be created from this encoded String as follows; this is done in the
	// CTscreencap constructor:
	// Base64.Decoder byte_decoder = Base64.getDecoder();
	// byte[] decoded_cursor_bytes = byte_decoder.decode(cursorStr);
	// try {
	//     BufferedImage cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
	// } catch (IOException ioe) {
	//     System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
	// }
	//
	// ScreencapTask includes code to draw this BufferedImage into the screencapture image
	// using Graphics2D (the reason we do this is because the screencapture doesn't include
	// the cursor).
	//
	public static String getEncodedImageString(String fileNameI, String formatNameI) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
		BufferedImage img = ImageIO.read(new File(fileNameI));
		boolean bFoundWriter = ImageIO.write(img, formatNameI, baos);
		if (!bFoundWriter) {
			throw new IOException( new String("No image writer found for format \"" + formatNameI + "\"") );
		}
		baos.flush();
		byte[] cursor_bytes = baos.toByteArray();
		Base64.Encoder byte_encoder = Base64.getEncoder();
		return byte_encoder.encodeToString(cursor_bytes);
	}
	
	/**
	 * 
	 * mouseDragged
	 * 
	 * Implement the mouseDragged method defined by interface MouseMotionListener.
	 * 
	 * This method implements the "guts" of our homemade window manager; this method
	 * handles moving and resizing the frame.
	 * 
	 * Why have we implemented our own window manager?  Since translucent panels
	 * can only be contained within undecorated Frames (see comments in the top
	 * header above) and since undecorated Frames don't support moving/resizing,
	 * we implement our own basic "window manager" by catching mouse move and drag
	 * events.
	 * 
	 * @author John P. Wilson
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseDragged(MouseEvent mouseEventI) {
		// System.err.println("mouseDragged: " + mouseEventI.getX() + "," + mouseEventI.getY());
		// Keep the screen capture area to at least a minimum width and height
		boolean bDontMakeThinner = false;
		boolean bDontMakeShorter = false;
		if (capturePanel.getHeight() < 20) {
			bDontMakeShorter = true;
		}
		if (capturePanel.getWidth() < 20) {
			bDontMakeThinner = true;
		}
		Point currentPoint = mouseEventI.getLocationOnScreen();
		int currentPosX = currentPoint.x;
		int currentPosY = currentPoint.y;
		int deltaX = 0;
		int deltaY = 0;
		if ( (mouseCommandMode != NO_COMMAND) && (mouseStartingPoint != null) ) {
			deltaX = currentPosX - mouseStartingPoint.x;
			deltaY = currentPosY - mouseStartingPoint.y;
		}
		int oldFrameWidth = guiFrame.getBounds().width;
		int oldFrameHeight = guiFrame.getBounds().height;
		if (mouseCommandMode == MOVE_FRAME) {
			Point updatedGUIFrameLoc = new Point(frameStartingBounds.x+deltaX,frameStartingBounds.y+deltaY);
			guiFrame.setLocation(updatedGUIFrameLoc);
		} else if (mouseCommandMode == RESIZE_FRAME_NW) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_N) {
			int newFrameWidth = frameStartingBounds.width;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_NE) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_E) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height;
			if (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_SE) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_S) {
			int newFrameWidth = frameStartingBounds.width;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_SW) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_W) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height;
			if (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else {
			// See if we need to go into a particular command mode
			mouseStartingPoint = null;
			frameStartingBounds = null;
			mouseCommandMode = getGUIFrameCommandMode(mouseEventI.getPoint());
			if (mouseCommandMode != NO_COMMAND) {
				mouseStartingPoint = mouseEventI.getLocationOnScreen();
				frameStartingBounds = guiFrame.getBounds();
			}
		}
	}
	
	/**
	 * 
	 * mouseMoved
	 * 
	 * Implement the mouseMoved method defined by interface MouseMotionListener.
	 * 
	 * This method is part of our homemade window manager; specifically, this method
	 * handles setting the appropriate mouse cursor based on where the user has
	 * positioned the mouse on the JFrame window.
	 * 
	 * Why have we implemented our own window manager?  Since translucent panels
	 * can only be contained within undecorated Frames (see comments in the top
	 * header above) and since undecorated Frames don't support moving/resizing,
	 * we implement our own basic "window manager" by catching mouse move and drag
	 * events.
	 * 
	 * @author John P. Wilson
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseMoved(MouseEvent mouseEventI) {
		// System.err.println("mouseMoved: " + mouseEventI.getX() + "," + mouseEventI.getY());
		mouseCommandMode = NO_COMMAND;
		// Set mouse Cursor based on the current mouse position
		int commandMode = getGUIFrameCommandMode(mouseEventI.getPoint());
		switch (commandMode) {
			case NO_COMMAND:		guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
			case MOVE_FRAME:		guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
			case RESIZE_FRAME_NW:	guiFrame.setCursor(new Cursor(Cursor.NW_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_N:	guiFrame.setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_NE:	guiFrame.setCursor(new Cursor(Cursor.NE_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_E:	guiFrame.setCursor(new Cursor(Cursor.E_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_SE:	guiFrame.setCursor(new Cursor(Cursor.SE_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_S:	guiFrame.setCursor(new Cursor(Cursor.S_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_SW:	guiFrame.setCursor(new Cursor(Cursor.SW_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_W:	guiFrame.setCursor(new Cursor(Cursor.W_RESIZE_CURSOR));
									break;
			default:				guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
		}
	}
	
	/**
	 * getGUIFrameCommandMode
	 * 
	 * Based on the given mouse position, determine what the corresponding mouse
	 * "command mode" should be.  For instance, if the mouse is near the upper-
	 * right corner of the window, this method will return RESIZE_FRAME_NE
	 * because the mouse is in position to resize the frame from that corner.
	 * 
	 * Several offsets are defined in variables to specify if the cursor is
	 * in a special region of the window for which moving or resizing can
	 * take place:
	 * 
	 *  	borderThickness:	a thickness all around the entire window, specifies
	 *  						a "frame" around the window; if the mouse is within
	 *  						this distance of the edge of the window, then this
	 *  						method will return one of the following "resize"
	 *  						commands, indicating that the mouse cursor is in
	 *  						position to resize the JFrame along that edge:
	 *  							RESIZE_FRAME_NW
	 *								RESIZE_FRAME_N
	 *								RESIZE_FRAME_NE
	 *								RESIZE_FRAME_E
	 *								RESIZE_FRAME_SE
	 *								RESIZE_FRAME_S
	 *								RESIZE_FRAME_SW
	 *								RESIZE_FRAME_W
	 *		cornerActiveLength:	the accepted vertical or horizontal distance from
	 *							a window corner in order to be considered within
	 *							the "active" region of that corner; for example,
	 *							let's say the cursor is along the top edge of the
	 *							JFrame, 15 pixels from the upper right (NE) corner
	 *							and cornerActiveLength = 20; in this case, the
	 *							cursor is within the "active" area for that corner
	 *							and this method would return RESIZE_FRAME_NE;
	 *							if instead the cursor was 21 pixels from the corner
	 *							along the top edge of the JFrame, the cursor would
	 *							not be considered within the active area for the
	 *							corner but is instead in the active area for resizing
	 *							at the top of the window and this method will
	 *							return RESIZE_FRAME_N
	 *		menubarHeight:		thickness at the top of the window which we
	 *							consider to be the menu bar region; if the mouse
	 *							is located within this region at the top of the
	 *							window but not within borderThickness of the very
	 *							edge of the JFrame, then this method will return
	 *							MOVE_FRAME, indicating that the mouse cursor is
	 *							in position to move the JFrame
	 * 
	 * @author John P. Wilson
	 * @param  mousePosI  Mouse position
	 */
	private int getGUIFrameCommandMode(Point mousePosI) {
		int borderThickness = 5;
		int cornerActiveLength = 20;
		int menubarHeight = 20;
		Rectangle frameBounds = guiFrame.getBounds();
		int frameWidth = frameBounds.width;
		int frameHeight = frameBounds.height;
		int cursorX = mousePosI.x;
		int cursorY = mousePosI.y;
		if ( (cursorY <= cornerActiveLength) && ( (cursorX <= borderThickness) || ( (cursorY <= borderThickness) && (cursorX <= cornerActiveLength) ) )  ) {
			// Mouse is in the upper left corner of the window
			return RESIZE_FRAME_NW;
		} else if ( (cursorY <= borderThickness) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is near the top of the window
			return RESIZE_FRAME_N;
		} else if ( (cursorY <= cornerActiveLength) && ( (cursorX >= (frameWidth-borderThickness)) || ( (cursorY <= borderThickness) && (cursorX >= (frameWidth-cornerActiveLength)) ) )  ) {
			// Mouse is in the upper right corner of the window
			return RESIZE_FRAME_NE;
		} else if ( (cursorY <= menubarHeight) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is in the top region of the window (in the menu bar region) but not near one of the corners
			return MOVE_FRAME;
		} else if ( (cursorY > cornerActiveLength) && (cursorY < (frameHeight-cornerActiveLength)) && (cursorX >= (frameWidth-borderThickness)) ) {
			// Mouse is on the right side of the window
			return RESIZE_FRAME_E;
		} else if ( (cursorY >= (frameHeight-cornerActiveLength)) && ( (cursorX >= (frameWidth-borderThickness)) || ( (cursorY >= (frameHeight-borderThickness)) && (cursorX >= (frameWidth-cornerActiveLength)) ) )  ) {
			// Mouse is in the lower right corner of the window
			return RESIZE_FRAME_SE;
		} else if ( (cursorY >= (frameHeight-borderThickness)) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is near the bottom of the window
			return RESIZE_FRAME_S;
		} else if ( (cursorY >= (frameHeight-cornerActiveLength)) && ( (cursorX <= borderThickness) || ( (cursorY >= (frameHeight-borderThickness)) && (cursorX <= cornerActiveLength) ) )  ) {
			// Mouse is in the lower left corner of the window
			return RESIZE_FRAME_SW;
		} else if ( (cursorY > cornerActiveLength) && (cursorY < (frameHeight-cornerActiveLength)) && (cursorX <= borderThickness) ) {
			// Mouse is on the left side of the window
			return RESIZE_FRAME_W;
		}
		return NO_COMMAND;
	}
}