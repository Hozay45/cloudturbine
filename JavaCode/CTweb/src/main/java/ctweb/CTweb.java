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
//---------------------------------------------------------------------------------	
// CTweb:  Web HTTP interface to CTreader
// Matt Miller, Cycronix

// 11/01/2016	revert to Jetty
// 07/16/2014	converted to NanoHTTPD
// 04/04/2016	updated to NanoHTTPD V2.2
// 02/18/2014	initial version using Jetty

/*
 URL Syntax:
 
 http://cloudturbine.net:/CT/Source/Channel?key1=value&key2=value
 
 where key-value pairs:
 
 key	value		examples						description
 
 t		time		123456789					time relative to tref (sec)
 r		tref		absolute,newest,oldest		time reference
 d		duration	100							time interval (sec)
 dt		datatype	s							format as string (s) default, binary (b), HTML (H)
 f		timefetch	t,d							f=t to fetch time-only (default time+data)

 */

package ctweb;
 
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import javax.imageio.ImageIO;
//import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;

import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.Connector;

import cycronix.ctlib.CTdata;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTreader;

//---------------------------------------------------------------------------------	

public class CTweb {
	
	private static final String servletRoot = "/CT";
	private static final String rbnbRoot = "/RBNB";
//	private static String rootFolder="CTdata";
	private static String rootFolder=null;				// for compat with CT2DB
	private static CTreader ctreader=null;
	public static boolean debug=false;
	public static boolean Debug=false;					// debug local plus CT
	private static boolean swapFlag = false;
//    private static String resourceBase = "CTweb";
    private static String resourceBase = null;			// search for resource base
    private static String sourceFolder = null;
    private static int MaxDat = 10000000;				// max number data elements to return (was 65536)
    private static long queryCount=0;
    private static String keyStoreFile="ctweb.jks";		// HTTPS keystore file path
    private static String keyStorePW="ctweb.pw";		// keystore PW
    private static String realmProps=null;				// authentication realm user/password info
	private static int	port = 8000;					// default port
	private static int sslport = 8443;					// HTTPS port (0 means none)
	private static String password=null;				// CTcrypto password
    private static int scaleImage=1;					// reduce image size by factor
    private static boolean fastSearch=false;			// fast channel search, reduces startup time 
	//---------------------------------------------------------------------------------	

    public static void main(String[] args) throws Exception {

    	if(args.length == 0) {
    		System.err.println("CTweb -r -x -X -F -p <port> -P <sslport> -f <webfolder> -s <sourceFolder> -k <keystoreFile> -K <keystorePW> -a <authenticationFile> -S <scaleImage> rootFolder");
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-r")) 	swapFlag = true;
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-X")) 	Debug=true; 
     		if(args[dirArg].equals("-F")) 	fastSearch = !fastSearch;
     		if(args[dirArg].equals("-p")) 	port = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-P")) 	sslport = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-f"))  	resourceBase = args[++dirArg]; 
     		if(args[dirArg].equals("-s"))  	sourceFolder = args[++dirArg]; 
     		if(args[dirArg].equals("-k"))	keyStoreFile = args[++dirArg];
     		if(args[dirArg].equals("-K"))	keyStorePW = args[++dirArg];
     		if(args[dirArg].equals("-a"))	realmProps = args[++dirArg];
     		if(args[dirArg].equals("-S")) 	scaleImage = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-e"))	password = args[++dirArg];

     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];

     	// If sourceFolder has been specified, make sure it exists
     	if ( (sourceFolder != null) && ( (new File(sourceFolder).exists() == false) || (new File(sourceFolder).isDirectory() == false) ) ) {
     		System.err.println("The source folder doesn't exist or isn't a directory.");
 			System.exit(0);
     	}

     	// set rootFolder
     	if(rootFolder == null && sourceFolder != null) {	// source is full path
     		rootFolder = new File(sourceFolder).getParent();
     		sourceFolder = new File(sourceFolder).getName();
     	}
     	else if(rootFolder == null) {				// check for a couple defaults
     		if		(new File("CTdata").exists()) 		rootFolder = "CTdata";
     		else if (new File(".."+File.separator+"CTdata").exists()) rootFolder = ".."+File.separator+"CTdata";
     		else if (new File("CloudTurbine").exists()) rootFolder = "CloudTurbine";
     		else {
     			System.err.println("Cannot find default data folder.  Please specify.");
     			System.exit(0);	
     		}
     	}
     	else {
     		if(!(new File(rootFolder).exists())) {
     			System.err.println("Cannot find specified data folder: "+rootFolder);
     			System.exit(0);	
     		}
     	}

     	// set resourceBase
     	if(resourceBase==null) {
     		if(new File("CTweb").exists()) 	resourceBase = "CTweb";
     		else							resourceBase = "http://webscan.cycronix.com";
     	}
     	
     	// create CT reader 
     	ctreader = new CTreader(rootFolder);
     	if(password!=null) ctreader.setPassword(password, true);		// optional decrypt
     	CTinfo.setDebug(Debug);
       
     	// setup and start Jetty HTTP server
     	Server server = setupHTTP();
        server.start();
        server.join();
    }

    
    //---------------------------------------------------------------------------------	
    // setup HTTP/S Jetty 
    
    private static Server setupHTTP() throws FileNotFoundException {

        // Create a basic jetty server object without declaring the port. 
        Server server = new Server();

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        if(sslport>0) {
        	http_config.setSecureScheme("https");
        	http_config.setSecurePort(sslport);
        	http_config.addCustomizer(new SecureRequestCustomizer(false,0L,false));	// disable HSTS, allow HTTP and HTTPS both
        }
        http_config.setOutputBufferSize(32768);
   
        // HTTP connector
        ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory(http_config));
        http.setPort(port);
        http.setIdleTimeout(30000);

        if(sslport>0) {		// setup HTTPS
        	File ksFile = new File(keyStoreFile);
        	if (ksFile.exists()) {
        		// SSL Context Factory for HTTPS
        		SslContextFactory sslContextFactory = new SslContextFactory();
        		
//                sslContextFactory.setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$");		// enable old ciphers?
        		
        		sslContextFactory.setKeyStorePath(ksFile.getAbsolutePath());
        		sslContextFactory.setKeyStorePassword(keyStorePW);
        		//        	sslContextFactory.setKeyManagerPassword(keypw);

        		// HTTPS Configuration
        		// On this HttpConfiguration object we add a
        		// SecureRequestCustomizer which is how a new connector is able to
        		// resolve the https connection before handing control over to the Jetty Server.
        		HttpConfiguration https_config = new HttpConfiguration(http_config);
        		SecureRequestCustomizer src = new SecureRequestCustomizer();
        		src.setStsMaxAge(2000);
        		src.setStsIncludeSubDomains(true);
        		https_config.addCustomizer(src);

        		// HTTPS connector
        		// We create a second ServerConnector, passing in the http configuration
        		// we just made along with the previously created ssl context factory.
        		// Next we set the port and a longer idle timeout.
        		ServerConnector https = new ServerConnector(server,
        				new SslConnectionFactory(sslContextFactory,HttpVersion.HTTP_1_1.asString()),
        				new HttpConnectionFactory(https_config));
        		https.setPort(sslport);
        		https.setIdleTimeout(500000);

        		// Here you see the server having multiple connectors registered with
        		// it, now requests can flow into the server from both http and https
        		// urls to their respective ports and be processed accordingly by jetty.

        		// Set the connectors
        		server.setConnectors(new Connector[] { http, https });
        	}
        	else {
        		System.err.println("Keystore file ("+keyStoreFile+") not found; HTTPS disabled.");
        		sslport = 0;
        		server.setConnectors(new Connector[] { http });
        	}
        }
        else server.setConnectors(new Connector[] { http });

        // Set a handler
        ServletHandler shandler = new ServletHandler();
        ServletHolder sholder;
        sholder = new ServletHolder(new CTServlet());

        sholder.setAsyncSupported(true);					// need fewer threads if non-blocking?
        sholder.setInitParameter("maxThreads", "100");		// how many is good?
        shandler.addServletWithMapping(sholder, "/*");
        
        setupAuthentication(server,shandler);				// set handler with optional authentication

        String msg;
        if(sslport > 0) msg = ", HTTP port: "+port+", HTTPS port: "+sslport;
        else				 msg = ", HTTP port: "+port;
        System.out.println("Server started.  webFolder: "+resourceBase+", dataFolder: "+rootFolder+msg+"\n");

        return server;
    }
    
    //---------------------------------------------------------------------------------	
    // setup authentication.  ref: https://www.eclipse.org/jetty/documentation/9.4.x/embedded-examples.html
    
    private static void setupAuthentication(Server server, ServletHandler shandler) {
    	if(realmProps == null) {		// notta
    		server.setHandler(shandler);
    		return;
    	}
    	
        // setup a hashmap based LoginService
        HashLoginService loginService = new HashLoginService("CTrealm",realmProps);
        server.addBean(loginService);

        // The ConstraintSecurityHandler allows matching of urls to different constraints. 
        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        server.setHandler(security);

        // This constraint requires authentication and that an
        // authenticated user be a member of a given set of roles
        Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
//        constraint.setRoles(new String[] { "user", "admin" });
        constraint.setRoles(new String[] { "**" });		// any role
        
        // Binds a url pattern with the previously created constraint.
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        // Next we set a BasicAuthenticator that checks the credentials
        // followed by the LoginService which is the store of known users, etc.
        security.setConstraintMappings(Collections.singletonList(mapping));
        security.setAuthenticator(new BasicAuthenticator());
        security.setLoginService(loginService);

        // set the given servlet handler on the to complete the simple handler chain.
        security.setHandler(shandler);
    }
    
    //---------------------------------------------------------------------------------	
    // callback for http requests
    @SuppressWarnings("serial")
    public static class CTServlet extends HttpServlet {
        
    	@Override
    	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			if(debug) System.err.println("doOptions, request: "+request.getPathInfo()+", queryCount: "+queryCount+", request.method: "+request.getMethod());
			response.addHeader("Access-Control-Allow-Origin", "*");            // CORS enable
			response.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");   // CORS enable
			response.addHeader("Access-Control-Allow-Headers", "If-None-Match");
//			response.addHeader("Allow", "GET, POST, HEAD, OPTIONS");
    	}
    	
    	@Override
    	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    		long startTime = System.nanoTime();
    		boolean doProfile=debug;
    		
    		if(debug) {
    			String uri = request.getScheme() + "://" +
    		             request.getServerName() + 
    		             ("http".equals(request.getScheme()) && request.getServerPort() == 80 || "https".equals(request.getScheme()) && request.getServerPort() == 443 ? "" : ":" + request.getServerPort() ) +
    		             request.getRequestURI() +
    		            (request.getQueryString() != null ? "?" + request.getQueryString() : "");
    			System.err.println("doGet, URI: "+uri+", queryCount: "+queryCount+", request.method: "+request.getMethod());
    		}
//    		String servletPath = request.getServletPath();
    		String pathInfo = request.getPathInfo();
    		
    		queryCount++;
    		StringBuilder sbresp = new StringBuilder(8192);			// estimate initial size

    		// server resource files
    		if(!pathInfo.startsWith(servletRoot)  && !pathInfo.startsWith(rbnbRoot)) {
    			try {    	    		// system clock utility
    				if(pathInfo.equals("/sysclock")) {
    					response.setContentType("text/plain");
    					response.getWriter().println(""+System.currentTimeMillis());
    					return;
    				}

    				InputStream in;
    				OutputStream out;

					response.setContentType(mimeType(pathInfo, "text/html"));	
    				if(resourceBase.startsWith("http")) {
    					if(pathInfo.equals("/")) pathInfo = "/webscan.htm";
    					in = new URL(resourceBase  + pathInfo).openStream();  
    					out = response.getOutputStream();
    				}
    				else {
    					if(pathInfo.equals("/")) {
    						if(new File(resourceBase+"/index.htm").exists()) 		pathInfo = "/index.htm";
    						else if(new File(resourceBase+"/index.html").exists()) 	pathInfo = "/index.html";
    						else													pathInfo = "/webscan.htm";
    					}
    					out = response.getOutputStream();
    					in = new FileInputStream(resourceBase+pathInfo);	// limit to resourceBase folder
    				}

    				// read/write response
    				byte[] buffer = new byte[4096];
    				int length;
    				while ((length = in.read(buffer)) > 0){
    					out.write(buffer, 0, length);
    				}
    				in.close();
    				out.flush();
    			} 
    			catch(Exception e) {
    				System.err.println("Exception on welcome file read, pathInfo: "+resourceBase+pathInfo+", Exception: "+e);
					formResponse(response, null);		// add CORS header even for error response
    				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    			}
    			return;
    		}

    		if(doProfile) System.err.println("doGet 1 time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));
			pathInfo = pathInfo.replace("//", "/");					// merge any empty double-slash layers
    		String pathParts[] = pathInfo.split("/");		// split into 2 parts: cmd/multi-part-path

    		try {
    			double duration=0., start=0.;
    			String reference="newest";			
    			String param;	char ftype='s';	  char fetch = 'b';
    			param = request.getParameter("d");	if(param != null) duration = Double.parseDouble(param);
    			param = request.getParameter("t");	if(param != null) { start = Double.parseDouble(param); reference="absolute"; }
    			param = request.getParameter("r");	if(param != null) reference = param;
    			param = request.getParameter("f");	if(param != null) fetch = param.charAt(0);
    			param = request.getParameter("dt");	if(param != null) ftype = param.charAt(0);

    			if(reference.equals("refresh")) {
    				ctreader.clearFileListCache();
    				formResponse(response,null);
    				return;
    			}
    			
    			if(pathInfo.equals(servletRoot+"/") || pathInfo.equals(rbnbRoot+"/")) pathInfo = servletRoot;		//  strip trailing slash

    			if(pathInfo.equals(servletRoot) || pathInfo.equals(rbnbRoot)) {			// Root level request for Sources
    				if(debug) System.err.println("source request: "+pathInfo);
    				
    				printHeader(sbresp,pathInfo,"/");
    				ArrayList<String> slist = new ArrayList<String>();

    				if(sourceFolder == null) slist = ctreader.listSources();
    				// if(sourceFolder == null) slist = ctreader.listSourcesRecursive();	// recursive now default
    				else					 slist.add(sourceFolder);

    				if(slist==null || slist.size()==0) sbresp.append("No Sources!");
    				else {
    					for(String sname : slist) {
    						sname = sname.replace("\\", "/");				// backslash not legal URL link
    						if(debug) System.err.println("src: "+sname);
//        					if(debug) System.err.println("src: "+sname+", sourceDiskSize: "+ (CTinfo.diskUsage(rootFolder+File.separator+sname,4096)/1024)+"K");
    						sbresp.append("<li><a href=\""+(pathInfo+"/"+sname)+"/\">"+sname+"/</a><br>");          
    					}
    				}

    				formResponse(response, sbresp);
    				return;
    			}
    			else if(pathInfo.endsWith("/")) {										// Source level request for Channels

    				if(debug) System.err.println("channel request: "+pathInfo);
    				if(pathParts.length < 3) {
						formResponse(response, null);		// add CORS header even for error response
						if(debug) System.err.println("warning, pathparts.length<3: "+pathParts.length);
        				response.sendError(HttpServletResponse.SC_NOT_FOUND);
        				return;
    				}
    				String sname = pathParts[2];
    				for(int i=3; i<pathParts.length; i++) sname += ("/"+pathParts[i]);		// multi-level source name
    				if(sname.endsWith("/")) sname = sname.substring(0,sname.length()-2);
    				if(debug) System.err.println("CTweb listChans for source: "+(rootFolder+File.separator+sname));
    				ArrayList<String> clist = ctreader.listChans(rootFolder+File.separator+sname,fastSearch);
    				
    				// auto-refresh cache with every listChans?
//    				ctreader.clearFileListCache();

    				if(clist == null) sbresp.append("<NULL>");
    				else {
    					if(ftype == 'H') {								// all chans in HTML table format

    						double time[] = null;
    						ArrayList<String[]> chanlist = new ArrayList<String[]>();

    						sbresp.append("<table id="+sname+">\n");
    						sbresp.append("<tr><th>Time</th>");

    						for(String chan : clist) {
    							sbresp.append("<th>"+chan+"</th>");
    							CTdata tdata = ctreader.getData(sname,chan,start,duration,reference);
    							if(time == null) time = tdata.getTime();			// presume all times follow first chan
    							chanlist.add(tdata.getDataAsString(CTinfo.fileType(chan,'s')));
    						}
    						sbresp.append("</tr>\n");
    						for(int i=0; i<time.length; i++) {
    							sbresp.append("<tr>");
    							sbresp.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
    							for(int j=0; j<chanlist.size(); j++) {
    								String c[] = chanlist.get(j);							// possibly unequal data sizes
    								if(i < c.length) sbresp.append("<td>"+c[i]+"</td>");
    							}
    							sbresp.append("</tr>\n");	
    						}
    						sbresp.append("</table>");
    					}
    					else {
    						printHeader(sbresp,pathInfo,"/"+pathParts[1]);
    						for(String cname : clist) {
    							if(debug) System.err.println(sname+"/chan: "+cname);
    							sbresp.append("<li><a href=\""+cname+"\">"+cname+"</a><br>");
    						}
    					}
    				}

    				formResponse(response, sbresp);
    				return;
    			}
    			else {																		// Channel level request for data
    				if(debug) System.err.println("data request: "+pathInfo);
	
    				String source = pathParts[2];
    				for(int i=3; i<pathParts.length-1; i++) source += ("/"+pathParts[i]);		// multi-level source name
    				
    				//    			String chan = pathParts[3];				//  presumes /CT/Source/Chan with no sub-level nesting
    				String chan = pathParts[pathParts.length-1];
//    				String sourcePath = rootFolder+File.separator+source;
    				String[] strdata=null;		

    				// setTimeOnly partially-implemented
    				if(fetch == 't') 	ctreader.setTimeOnly(true);		// don't waste time/memory getting data...
    				else    			ctreader.setTimeOnly(false);

    				CTdata tdata = ctreader.getData(source,chan,start,duration,reference);
    				if(doProfile) System.err.println("doGet R time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));

    				if(tdata == null) {		// empty response for WebTurbine compatibility
    					if(debug) System.err.println("No such channel: "+pathInfo+", chan: "+chan+", start: "+start+", duration: "+duration+", refernce: "+reference);					
    					formResponse(response, null);		// add CORS header even for error response
        				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    					return;
    				}
    				else {
    					tdata.setSwap(swapFlag);
    					double time[] = tdata.getTime();
    					if(time == null) {
    						if(debug) System.err.println("Oops, got data but no time data?: "+pathInfo);
    						formResponse(response, null);		// add CORS header even for error response
    	    				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    	    				return;
    					}
    					
    					int numData = time.length;
    					if(debug) System.err.println("--------CTweb getData: "+chan+", numData: "+numData+", fetch: "+fetch+", ftype: "+ftype+", pathInfo: "+pathInfo);

        				// check for If-None-Match and skip duplicates.
//        				if(duration==0 && fetch=='b' && reference.equals("absolute")) {		// only works for single-object requests
    					if(numData>0) {
    						String ifnonematch = request.getHeader("If-None-Match");
    						if(ifnonematch != null) {
    							String[] matchparts = ifnonematch.split(":");
    							if(matchparts.length == 2 && matchparts[1].length()>0) {
    								String matchchan = matchparts[0];
    								double matchtime = Double.parseDouble(matchparts[1]);		// int-msec
    								//        							long gottime = (long)(1000.* time[0]);				// int-msec for compare
    								double gottime = 1000.* time[time.length-1];	// int-msec for compare to last (most recent) got-time
    								String reqchan = source + "/" + chan;				// reconstruct full path
    								if(reqchan.startsWith("/")) reqchan = reqchan.substring(1);		// strip leading '/' if present
    								if(debug) System.err.println("ifnonematch, gottime: "+gottime+", matchtime: "+matchtime+", matchchan: "+matchchan+", reqchan: "+reqchan);
									if(doProfile) System.err.println("doGet 2a time: "+((System.nanoTime()-startTime)/1000000.)+" ms");

    								if(Math.abs(matchtime-gottime)<=1 && matchchan.equals(reqchan)) {		// account for msec round off error
    									// add header info about time limits
    									// JPW, in next 2 calls, change from sourcePath to source (ie, don't use full path)
//    									double oldTime = ctreader.oldTime(source,chan);
//    									double newTime = ctreader.newTime(source,chan);
    									double[] tlimits = ctreader.timeLimits(source, chan);
    									double oldTime = tlimits[0];
    									double newTime = tlimits[1];
    									if(doProfile) System.err.println("doGet 2b time: "+((System.nanoTime()-startTime)/1000000.)+" ms");

    									double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
    									formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);

    									formResponse(response,null);
    									response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
    									if(doProfile) System.err.println("doGet 2c time: "+((System.nanoTime()-startTime)/1000000.)+" ms");

    									if(debug) System.err.println("NOT_MODIFIED: "+matchchan+", reqTime: "+start+", gotTime: "+gottime+", ref: "+reference+", newTime: "+newTime);
    									return;
    								}
    							}
    						}
    					}
        					
    					if(numData > MaxDat && ftype != 'b') {		// unlimited binary fetch
    						System.err.println("CTweb: limiting output points to: "+MaxDat);
    						numData = MaxDat;
    					}
    					
    					// if(time.length == 0) System.err.println("CTweb warning: no data!");
    					if(numData > 0) {
    						if(ftype == 's' /* && fetch=='b' */) ftype = CTinfo.fileType(chan,'s');	// over-ride for certain binary types
    						if(fetch=='t') ftype ='s';								// time-only data returned as string
    						if(debug) System.err.println("getData: "+chan+"?t="+start+"&d="+duration+"&r="+reference+", ftype: "+ftype);

    						switch(ftype) {

    						// binary types returned as byteArrays (no time stamps sent!)
    						case 'b':	
    						case 'B':   
    							byte[] bdata = tdata.getDataAsByteArray();		// get as single byte array vs chunks

	   							// add header info about time limits
    							// JPW, in next 2 calls, change from sourcePath to source (ie, don't use full path)
    	//						double oldTime = ctreader.oldTime(source,chan);
    	//						double newTime = ctreader.newTime(source,chan);
								double[] tlimits = ctreader.timeLimits(source, chan);
								double oldTime = tlimits[0];
								double newTime = tlimits[1];
    							double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
//								formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);
    						
    							if(chan.toLowerCase().endsWith(".jpg")) 		response.setContentType("image/jpeg");
    							else if(chan.toLowerCase().endsWith(".wav")) 	response.setContentType("audio/wav");
    							else											response.setContentType("application/octet-stream");

    							if(bdata == null || bdata.length==0) {
    								if(debug) System.err.println("No data for request: "+pathInfo);
    								formHeader(response, 0., 0., oldTime, newTime, lagTime);
    								formResponse(response, null);		// add CORS header even for error response
    								response.sendError(HttpServletResponse.SC_NOT_FOUND);
    								return;
    							}
    							else {
    								formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);
    								formResponse(response,null);
    							}

    							// down-size large images
    							if(chan.endsWith(".jpg") && (scaleImage>1) && bdata.length>100000) {	
    								if(bdata.length<200000 && scaleImage>2) bdata = scale(bdata, 2);
    								else									bdata = scale(bdata, scaleImage);	
    							}
    							
    							if(bdata.length < 65536) {	// unchunked
//    								System.err.println("b.length: "+bdata.length);
    								response.setContentLength(bdata.length);
    								response.getOutputStream().write(bdata);
    							}
    							else {			// chunked transfer
    								OutputStream out = response.getOutputStream();
    								InputStream input = new ByteArrayInputStream(bdata);		// only return 1st image?
    								byte[] buffer = new byte[16384];
    								int length;
    								long totRead = 0;
    								while ((length = input.read(buffer)) > 0){
    									totRead += length;
    									out.write(buffer, 0, length);
    								}
    								if(debug) System.err.println("chunked transfer for: "+chan+", totalBytes: "+totRead);
    								input.close();
    								out.flush();
    							}
    							if(doProfile) System.err.println("doGet B time: "+((System.nanoTime()-startTime)/1000000.)+" ms");

    							if(debug) System.err.println("binary data response, bytes: "+bdata.length);
    							return;

    							// HTML table format (for import to spreadsheets)
    						case 'H':			
    							strdata = tdata.getDataAsString(CTinfo.fileType(chan,'s'));		// convert any/all numeric types to string
    							if(strdata != null) {
    								sbresp.append("<table id="+source+"/"+chan+">\n");
    								sbresp.append("<tr><th>Time</th><th>"+source+"/"+chan+"</th></tr>");
    								for(int i=time.length-numData; i<numData; i++) {
    									sbresp.append("<tr>");
    									if(fetch != 'd') sbresp.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
    									if(fetch != 't') sbresp.append("<td>"+strdata[i]+"</td>");
    									sbresp.append("</tr>\n");	
    								}
    								sbresp.append("</table>");
    								formResponse(response, sbresp);
        							if(debug) System.err.println("HTML data response, length: "+sbresp.length());
    								return;
    							}
    							else {
    								System.err.println("Unrecognized ftype: "+ftype);
    								formResponse(response, null);		// add CORS header even for error response
    								response.sendError(HttpServletResponse.SC_NOT_FOUND);
    								return;
    							}

    							// all other types returned as rows of time,value strings
    						default:
    							if(fetch=='t') {
    								for(int i=time.length-numData; i<numData; i++) sbresp.append(formatTime(time[i])+"\n");		// most recent
    							}
    							else {
    								strdata = tdata.getDataAsString(ftype);		// convert any/all numeric types to string
    								if(strdata != null) {
    									if(fetch=='d') 
    										for(int i=time.length-numData; i<numData; i++) sbresp.append(strdata[i]+"\n");	
    									else
    										for(int i=time.length-numData; i<numData; i++) sbresp.append(formatTime(time[i]) +","+strdata[i]+"\n");	
    								}
    								else {
    									System.err.println("Unrecognized ftype: "+ftype);
    									formResponse(response, null);		// add CORS header even for error response
    									response.sendError(HttpServletResponse.SC_NOT_FOUND);
    									return;
    								}
    							}
    							// add header info about time limits
    							// JPW, in next 2 calls, change from sourcePath to source (ie, don't use full path)
//    							oldTime = ctreader.oldTime(source,chan);
//    							newTime = ctreader.newTime(source,chan);
								tlimits = ctreader.timeLimits(source, chan);
								oldTime = tlimits[0];
								newTime = tlimits[1];
    							lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
    							formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);  
    							//    								response.setContentType(mimeType(pathInfo, "text/html"));
    							response.setContentType("text/html");		// all string data in this case!
    							formResponse(response, sbresp);
    							if(doProfile) System.err.println("doGet S time: "+((System.nanoTime()-startTime)/1000000.)+" ms, chan: "+chan);
    							if(debug) System.err.println("CSV data response, length: "+sbresp.length());

    							return;
    						}
    					}
    					else {
    						// add header info about time limits even if no data
    						if(debug) System.err.println("No data for: "+pathInfo);
    						// JPW, in next 2 calls, change from sourcePath to source (ie, don't use full path)
//    						double oldTime = ctreader.oldTime(source,chan);
//    						double newTime = ctreader.newTime(source,chan);
							double[] tlimits = ctreader.timeLimits(source, chan);
							double oldTime = tlimits[0];
							double newTime = tlimits[1];
    						double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
    						formHeader(response, start, start+duration, oldTime, newTime, lagTime);
        					formResponse(response, null);		// add CORS header even for error response
            				response.sendError(HttpServletResponse.SC_NOT_FOUND);
            				return;
    					}
    				}
    			}
    		} catch(Exception e) { 
    			System.err.println("CTweb doGet Exception: "+e); 
    			if(debug) e.printStackTrace(); 
    		}

    		if(debug) System.err.println("Unable to respond to: "+pathInfo);
			formResponse(response, null);		// add CORS header even for error response
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
    	}
    }
    
    //---------------------------------------------------------------------------------	
    private static void formHeader(HttpServletResponse response, double startTime, double endTime, double oldTime, double newTime, double lagTime) {
    	response.addHeader("time", formatTime(startTime));								// sec
//    	response.addHeader("time", formatTime(endTime));								// sec

		response.addHeader("Last-Modified", ""+new Date((long)(1000*endTime)).toGMTString());			// msec
		
		double duration = endTime - startTime;
		response.addHeader("duration", formatTime(duration));
		response.addHeader("X-Duration", formatTime(duration));		// compatible with WebTurbine

		response.addHeader("oldest", formatTime(oldTime));
		response.addHeader("newest", formatTime(newTime));
		response.addHeader("lagtime",formatTime(lagTime));

		response.addHeader("cache-control", "private, max-age=3600");			// enable browse cache
		
		if(debug) System.err.println("+++CTweb: time: "+startTime+", endTime: "+endTime+", duration: "+duration+", oldest: "+oldTime+", newest: "+newTime+", hlag: "+lagTime);
    }
    
    //---------------------------------------------------------------------------------	
 
    private static void formResponse(HttpServletResponse resp, StringBuilder sbresp) {
		resp.addHeader("Access-Control-Allow-Origin", "*");            // CORS enable
		resp.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");   // CORS enable
		resp.addHeader("Access-Control-Expose-Headers", "oldest,newest,duration,time,lagtime");
		if(sbresp == null) return;
		try {
			resp.getWriter().println(sbresp.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private static String formatTime(double time) {
		if(((long)time) == time) return(Long.toString((long)time));
		else					 return Double.toString(time);		
//		else					 return new DecimalFormat("0.000").format(time);		// loses precision
    }
    
    private static void printHeader(StringBuilder response, String path, String uplevel) {
    	try {
    		String title = "Directory listing for: "+path;
    		response.append("<head><title>"+title+"</title></head>");
    		if(uplevel != null) response.append("<a href=\""+uplevel+"\">[Up one level]</a><br>");
    		response.append("<h3>"+title+"</h3>");
    	} catch(Exception e) {
    		System.err.println("oops, exception: "+e);
    	}
    }
    
    private static String mimeType(String fname, String deftype) {
		String mime = deftype;
		if		(fname.toLowerCase().endsWith(".css")) mime = "text/css";
		else if	(fname.toLowerCase().endsWith(".js")) mime = "application/javascript";
		else if	(fname.toLowerCase().endsWith(".jpg")) mime = "image/jpeg";
		else if	(fname.toLowerCase().endsWith(".png")) mime = "image/png";
		else if	(fname.toLowerCase().endsWith(".wav")) mime = "audio/wav";
		else if (fname.toLowerCase().endsWith(".csv")) mime = "text/css";
		if(debug) System.err.println("fname: "+fname+", mime type: "+mime);
		return mime;
    }
    
    //---------------------------------------------------------------------------------	
    // Scale image.jpg to smaller size to save bandwidth
    
    private static byte[] scale(byte[] bdata, int scaleImage) throws Exception {
    	if(scaleImage <= 1) return bdata;
    	
		BufferedImage img=ImageIO.read(new ByteArrayInputStream(bdata));
		int targetWidth = img.getWidth()/scaleImage;
		int targetHeight = img.getHeight()/scaleImage;
		
        int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        BufferedImage scratchImage = null;
        Graphics2D g2 = null;

        int w = img.getWidth();
        int h = img.getHeight();

        int prevW = w;
        int prevH = h;

        do {
            if (w > targetWidth) {
                w /= 2;
                w = (w < targetWidth) ? targetWidth : w;
            }

            if (h > targetHeight) {
                h /= 2;
                h = (h < targetHeight) ? targetHeight : h;
            }

            if (scratchImage == null) {
                scratchImage = new BufferedImage(w, h, type);
                g2 = scratchImage.createGraphics();
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(ret, 0, 0, w, h, 0, 0, prevW, prevH, null);

            prevW = w;
            prevH = h;
            ret = scratchImage;
        } 
        while (w != targetWidth || h != targetHeight);

        if (g2 != null) g2.dispose();

        if (targetWidth != ret.getWidth() || targetHeight != ret.getHeight()) {
            scratchImage = new BufferedImage(targetWidth, targetHeight, type);
            g2 = scratchImage.createGraphics();
            g2.drawImage(ret, 0, 0, null);
            g2.dispose();
            ret = scratchImage;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	ImageIO.write( ret, "jpg", baos );
    	baos.flush();
    	byte[] bdata2 = baos.toByteArray();
    	baos.close();
    	
		if(debug) System.err.println("Scale image "+scaleImage+"x, "+bdata.length+" to "+bdata2.length+" bytes");

        return bdata2;
    }
}


