/*
Copyright 2008 Ilias Bergstrom.
  
This file is part of "Mother".

Mother is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Foobar is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Mother.  If not, see <http://www.gnu.org/licenses/>.
 
onar3d@hotmail.com, www.onar3d.com
 
 */

import processing.core.*;
import processing.opengl.*;

import oscP5.*;
import mpe.config.FileParser;
import netP5.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import javax.swing.ImageIcon;

import codeanticode.glgraphics.GLConstants;

import com.illposed.osc.OSCListener;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortIn;
import com.illposed.osc.OSCPortOut;

import java.text.NumberFormat;
import java.util.*;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import foetus.*;

//import fullscreen.*;  

//import onar3d.mothergraphics.*;
/*
 import org.apache.log4j.*;
 import org.apache.log4j.Logger;
 */

/*
 "-Djava.library.path=C:\Program Files\Processing\libraries\opengl\library"
 "-XX:-PrintConcurrentLocks"
 "-XX:+DisplayVMOutputToStderr"
 "-XX:+PrintClassHistogram"
 */

public class Mother extends PApplet // implements Tool
{
	PGraphicsOpenGL pgl;
	GL opengl;
	GLU glu;

	/* a NetAddress contains the ip address and port number of a remote location in the network. */
	private NetAddress 		oscBroadcastLocation;
	private int 			m_osc_send_port;
	private int 			m_osc_receive_port;
	private String 			m_IP;
	private SynthContainer 	m_SynthContainer;
	private String 			m_Synth_Folder;
	private int 			m_Width;
	private int 			m_Height;
	private FileParser 		fp;
	private PrintWriter 	output;
	private boolean 		m_FullScreen;
	private int 			m_OutputScreen;
	private boolean 		m_WriteImage = false;
	private float 			m_FrameRate = 30f;
	private String 			m_ImageFolder;
	private float 			m_SpeedFraction;
	private boolean 		firstProfiledFrame; // Frames-per-second computation
	private int 			profiledFrameCount;
	private int 			numDrawElementsCalls;
	private long 			startTimeMillis;
	private boolean 		m_Stereo;
	private boolean 		m_Billboard;
	static int 				pos_X;
	static int 				pos_Y;
	
	// private Logger logger = null;

	ArrayList<Message> m_MessageStack;

	public float 	getSpeedFraction()	{	return m_SpeedFraction;	}
	public boolean	getBillboardFlag()	{	return m_Billboard; }

	public int getChildWidth()
	{
		if(m_Stereo)
			return m_Width/2;
		else
			return m_Width;
	}
	
	public int getChildHeight()
	{
		return m_Height;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see processing.core.PApplet#setup()
	 */
	public void setup()
	{
		ImageIcon titlebaricon = new ImageIcon(loadBytes("mother_icon.jpg"));
		frame.setIconImage(titlebaricon.getImage());

		registerDispose(this);
		registerPre(this);
		registerPost(this);

		size(m_Width, m_Height, GLConstants.GLGRAPHICS);

		frameRate(m_FrameRate / m_SpeedFraction);

		hint(ENABLE_OPENGL_4X_SMOOTH); // Just to trigger renderer change.

		pgl 	= (PGraphicsOpenGL) g;
		opengl 	= pgl.gl;
		glu 	= ((PGraphicsOpenGL) g).glu;

		if(m_Stereo)
		{
//			perspective(PI/3.0f,1f,0.1f,1000f); //this is needed ot stop the images being squashed	
			noStroke();
		}
		
		m_SynthContainer = new SynthContainer(m_Synth_Folder);

		m_MessageStack = new ArrayList<Message>();

		listenToOSC();

		pgl.beginGL();
		opengl.setSwapInterval(1); // set vertical sync on
		pgl.endGL();

		this.frame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				try
				{
					this.finalize();
					System.exit(0);
				}
				catch (Throwable e1)
				{
					e1.printStackTrace();
				}
			}
		});

		// System.setProperty("log4j.configuration", "log4j.properties");
		// logger = Logger.getLogger(this.getName());
		// BasicConfigurator.configure();
	}

	public void pre() 	{}

	public void post()	{}

	public void dispose()	{ System.out.println("Disposed of."); }

	/*
	 * 
	 */
	private void PreDrawChildUpdate(PApplet child)
	{
		child.mouseX 		= this.mouseX;
		child.mouseY 		= this.mouseY;
		child.mousePressed 	= this.mousePressed;
		child.keyPressed 	= this.keyPressed;
		child.key 			= this.key;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see processing.core.PApplet#draw()
	 */
	public void draw()
	{
		ChildWrapper current;

		// From ProcessingHacks, for fullscreen without problems
		// where window minimizes when focus is lost.
		if (m_FullScreen)
			frame.setLocation(pos_X, pos_Y);

		if (frame.getExtendedState() == 1) // If minimized, expand again
			frame.setExtendedState(0);

		dealWithMessageStack(); // Dealing with message stack

		opengl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // Set The Clear Color To Black
		opengl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		synchronized (m_SynthContainer)
		{
			for (int i = 0; i < m_SynthContainer.Synths().size(); i++)
			{
				current = (ChildWrapper) m_SynthContainer.Synths().get(i);

				PreDrawChildUpdate(current.Child());

				CallRegisteredMethods(current, "preMethods");

				opengl.glEnable(GL.GL_BLEND);
				// opengl.glDisable(GL.GL_DEPTH_TEST); // Disables Depth Testing

				// //For testing (getting opengl state)
				// IntBuffer arg1 = IntBuffer.allocate(1);
				// opengl.glGetIntegerv(GL.GL_BLEND_DST, arg1);
				// println(arg1.get(0));

				pgl.colorMode(RGB, 255);

//				if (m_Stereo)
//				{
//					pushStyle();
//					
//					// Left Eye
//					// This means anything we draw will be limited to the left half of the screen 
//					camera(m_Width/4.0f, m_Height/2.0f, (m_Height/2.0f) / tan(PI*60.0f / 360.0f), m_Width/4.0f, m_Height/2.0f, 0, 0, 1, 0);
//					
//					opengl.glPushMatrix();
//										
//					opengl.glViewport(0, 0, m_Width/2, m_Height);
//					
//					opengl.glPushMatrix();
//
//					opengl.glBlendFunc(current.GetBlending_Source(), current.GetBlending_Destination());
//
//					// logger.info("Starting drawing");
//					opengl.glPushAttrib(GL.GL_ALL_ATTRIB_BITS);
//					
//					current.draw();
//					
//					CallRegisteredMethods(current, "drawMethods");
//
//					opengl.glPopAttrib();
//					// logger.info("Ending drawing");
//
//					opengl.glPopMatrix();
//					opengl.glPopMatrix();
//					
//					popStyle();
//					
//					pushStyle();
//					
//					// Right Eye					
//					// This means anything we draw will be limited to the left half of the screen  
//					camera(m_Width/4.0f, m_Height/2.0f, (m_Height/2.0f) / tan(PI*60.0f / 360.0f), m_Width/4.0f, m_Height/2.0f, 0, 0, 1, 0);
//
//					opengl.glPushMatrix();
//
//					opengl.glViewport(m_Width/2, 0, m_Width/2, m_Height);
//					
//					opengl.glPushMatrix();
//
//					opengl.glBlendFunc(current.GetBlending_Source(), current.GetBlending_Destination());
//
//					// logger.info("Starting drawing");
//					
//					opengl.glPushAttrib(GL.GL_ALL_ATTRIB_BITS);
//					
//					current.draw();
//
//					CallRegisteredMethods(current, "drawMethods");
//
//					opengl.glPopAttrib();
//					// logger.info("Ending drawing");
//
//					opengl.glPopMatrix();
//					opengl.glPopMatrix();
//					
//					popStyle();
//				}
//				else
//				{
					opengl.glPushMatrix();

					opengl.glBlendFunc(current.GetBlending_Source(), current.GetBlending_Destination());

					// logger.info("Starting drawing");
					pushStyle();
					opengl.glPushAttrib(GL.GL_ALL_ATTRIB_BITS);
					current.draw(m_Stereo);

					CallRegisteredMethods(current, "drawMethods");

					opengl.glPopAttrib();
					popStyle();
					// logger.info("Ending drawing");

					opengl.glPopMatrix();
//				}

				opengl.glDisable(GL.GL_BLEND);

				CallRegisteredMethods(current, "postMethods");
			}
		}

		// float m = millis(); // For timing image recording
		handleImageRecording();
		// System.out.println(millis()-m);

		printFrameRate();
	}

	protected void finalize()
	{
		System.out.println("FINALIZING");
		output.flush(); // Write the remaining data
		output.close(); // Finish the file
	}

	public void keyPressed()
	{
		PApplet child;
		Method keyMethod;

		switch (key)
		{
		case 'r':
			m_WriteImage = !m_WriteImage;
			break;
		}

		// For forwarding keyPressed messages to synths. Not really necessary though as all
		// communication is supposed to be over OSC.

		for (int i = 0; i < m_SynthContainer.Synths().size(); i++)
		{
			child = ((ChildWrapper) m_SynthContainer.Synths().get(i)).Child();

			// Handling messages to synths
			try
			{
				child.keyEvent = this.keyEvent;
				child.key = this.key;
				child.keyCode = this.keyCode;

				keyMethod = child.getClass().getMethod("keyPressed", new Class[] {});
				keyMethod.invoke(child, new Object[] {});
			}
			catch (Exception e)
			{
				println("CRASH keyPressed" + e.getMessage());
			}
		}
	}

	public void motherAcceptMessage(Date time, OSCMessage message)
	{
		Message m;

		synchronized (m_MessageStack)
		{
			m = new Message();
			m.time = time;
			m.message = message;

			m_MessageStack.add(m);

			if (m_MessageStack.size() > 5000)
			{
				System.out.println("Clearing message stack");
				m_MessageStack.clear();
			}
		}
	}

	/*
	 * incoming osc message are forwarded to the oscEvent method.
	 */
	void oscEvent(OscMessage theOscMessage)
	{
		PApplet child;
		Method oscEventMethod;

		String addrPattern = theOscMessage.addrPattern();
		String typetag = theOscMessage.typetag();
		String[] splits = addrPattern.split("/");

		// logger.info("Got message: " + theOscMessage.toString());

		/* check if theOscMessage has the address pattern we are looking for. */
		if (splits.length >= 2 && (splits[1].compareTo("Mother") == 0))
		{
			synchronized (m_SynthContainer)
			{
				if (splits[2].compareTo("Get_synth_names") == 0)
				{
					OSCPortOut sender;
					try
					{
						InetAddress ip = InetAddress.getByName(m_IP);
						sender = new OSCPortOut(ip, m_osc_send_port);

						ArrayList list = new ArrayList();
						for (Enumeration<String> e = m_SynthContainer.get_Synth_Names().keys(); e.hasMoreElements();)
						{
							list.add(e.nextElement());
						}

						Object args[] = new Object[list.size()];

						for (int i = 0; i < list.size(); i++)
						{
							args[i] = list.get(i);
						}

						OSCMessage msg = new OSCMessage("/Synth_names", args);

						sender.send(msg);
					}
					catch (UnknownHostException e1)
					{
						e1.printStackTrace();
					}
					catch (SocketException e1)
					{
						e1.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
				else if (splits[2].compareTo("Add_synth") == 0)
				{
					if (theOscMessage.checkTypetag("ss"))
					{
						if (!m_SynthContainer.contains(theOscMessage.get(1).stringValue()))
						{
							this.redraw = false;
							noLoop();

							ChildWrapper wrapper = m_SynthContainer.Add(	theOscMessage.get(1).stringValue(), 
																			theOscMessage.get(0).stringValue(), 
																			this);

							sendSupportedMessages(wrapper);

							loop();
							this.redraw = true;
						}
					}
				}
				else if (splits[2].compareTo("Reset") == 0)
				{
					m_SynthContainer.reset();
				}
				else if (splits[2].compareTo("Remove_synth") == 0)
				{
					if (theOscMessage.checkTypetag("s"))
					{
						ChildWrapper w = m_SynthContainer.Remove(theOscMessage.get(0).stringValue());

						CallRegisteredMethods(w, "disposeMethods");
					}
				}
				else if (splits[2].compareTo("Move_synth") == 0)
				{
					if (theOscMessage.checkTypetag("si"))
					{
						m_SynthContainer.Move(theOscMessage.get(0).stringValue(), theOscMessage.get(1).intValue());
					}
				}
				else if (splits[2].compareTo("Set_synth_blending") == 0)
				{
					if (theOscMessage.checkTypetag("sii"))
					{
						m_SynthContainer.Set_Synth_Blending(theOscMessage.get(0).stringValue(), theOscMessage.get(1)
								.intValue(), theOscMessage.get(2).intValue());
					}
				}
				else if (splits[2].compareTo("Child") == 0 && splits.length >= 4)
				{
					StringBuffer newAddrPattern = new StringBuffer();
					String childName;

					for (int pos = 4; pos < splits.length; pos++)
					{
						newAddrPattern.append("/" + splits[pos]);
					}

					for (int i = 0; i < m_SynthContainer.Synths().size(); i++)
					{
						child = ((ChildWrapper) m_SynthContainer.Synths().get(i)).Child();
						childName = ((ChildWrapper) m_SynthContainer.Synths().get(i)).GetName();

						if (childName.compareTo(splits[3]) == 0)
						{
							if (splits[4].compareTo("Get_Supported_Messages") == 0)
							{
								sendSupportedMessages((ChildWrapper) m_SynthContainer.Synths().get(i));
							}
							else
							// Handling messages to synths
							{
								try
								{
									// removing "/Mother/Child/Synth_Name" from address pattern
									theOscMessage.setAddrPattern(newAddrPattern.toString());

									oscEventMethod = child.getClass().getDeclaredMethod("oscEvent",
											new Class[] { OscMessage.class });

									oscEventMethod.invoke(child, new Object[] { theOscMessage });
								}
								catch (Exception e)
								{
									println("CRASH Child oscEvent" + childName + e.getStackTrace());
									println(e.getStackTrace());
								}
							}

							break;
						}
					}
				}
				else if (splits[2].compareTo("Record") == 0)
				{
					if (theOscMessage.checkTypetag("i"))
					{
						int in = theOscMessage.get(0).intValue();

						if (in == 1)
						{
							m_WriteImage = true;
							System.out.println("Recording!");
						}
						else if (in == 0)
						{
							m_WriteImage = false;
							System.out.println("Stopped Recording!");
							// loop();
						}
					}
				}

			}
		}
		else
		// Message not for mother
		{
			// println("Unhandled OSC message: " + theOscMessage.addrPattern());
		}

		// logger.info("Finished with message: " + theOscMessage.toString());
	}

	protected void sendPicWritingStartedMessage()
	{
		OSCPortOut sender;
		try
		{
			// (m_IP, m_osc_send_port)
			InetAddress ip = InetAddress.getByName(m_IP);
			sender = new OSCPortOut(ip, m_osc_send_port);

			ArrayList list = new ArrayList();

			list.add((float) m_FrameRate);

			Object args[] = new Object[list.size()];

			for (int i = 0; i < list.size(); i++)
			{
				args[i] = list.get(i);
			}

			OSCMessage msg = new OSCMessage("/Mother_Pic_Writing_Started/", args);

			sender.send(msg);
		}
		catch (UnknownHostException e1)
		{
			e1.printStackTrace();
		}
		catch (SocketException e1)
		{
			e1.printStackTrace();
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}

		redraw();
	}

	protected void sendNextFrameMessage()
	{
		OSCPortOut sender;
		try
		{
			// (m_IP, m_osc_send_port)
			InetAddress ip = InetAddress.getByName(m_IP);
			sender = new OSCPortOut(ip, m_osc_send_port);

			ArrayList list = new ArrayList();

			list.add((float) m_FrameRate);

			Object args[] = new Object[list.size()];

			for (int i = 0; i < list.size(); i++)
			{
				args[i] = list.get(i);
			}

			OSCMessage msg = new OSCMessage("/Mother_Next_Frame/", args);

			sender.send(msg);
		}
		catch (UnknownHostException e1)
		{
			e1.printStackTrace();
		}
		catch (SocketException e1)
		{
			e1.printStackTrace();
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}

		redraw();
	}

	protected void sendSupportedMessages(ChildWrapper wrapper)
	{
		PApplet child = wrapper.Child();
		String childName = wrapper.GetName();

		Foetus f = null;

		try
		{
			f = (Foetus) child.getClass().getField("f").get(child);
		}
		catch (Exception e)
		{
			println("CRASH: Accessing child's foetus failed!" + e.getMessage());
		}

		Hashtable<String, String> supportedMessages = f.getSupportedMessages();
		Enumeration<String> e = supportedMessages.elements();

		OSCPortOut sender;
		try
		{
			// (m_IP, m_osc_send_port)
			InetAddress ip = InetAddress.getByName(m_IP);
			sender = new OSCPortOut(ip, m_osc_send_port);

			ArrayList list = new ArrayList();
			for (Enumeration<String> ek = supportedMessages.keys(); ek.hasMoreElements();)
			{
				list.add((String) ek.nextElement());
				list.add((String) e.nextElement());
			}

			Object args[] = new Object[list.size()];

			for (int i = 0; i < list.size(); i++)
			{
				args[i] = list.get(i);
			}

			OSCMessage msg = new OSCMessage("/Synth_supported_messages/" + childName, args);

			sender.send(msg);
		}
		catch (UnknownHostException e1)
		{
			e1.printStackTrace();
		}
		catch (SocketException e1)
		{
			e1.printStackTrace();
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
	}

	// -Djava.library.path=src/processing/opengl/library

	// For remote debugging, haven't gotten it right yet though: -Xdebug
	// -Xrunjdwp:transport=dt_socket,address=8000,suspend=n,server=y

	public void init()
	{
		// Useless initializations, unless the program doesn't fint the .ini file at all...
		m_Width = 640;
		m_Height = 480;

		m_FullScreen = true;

		// For OSC
		m_IP = "127.0.0.1";
		m_osc_receive_port = 7005;
		m_osc_send_port = 5432;
		m_Synth_Folder = "X:\\Lumia Synths";

		// Loading setup values from .ini file
		loadIniFile(sketchPath("mother" + ".ini"));

		if (frame != null && m_FullScreen == true)
		{
			frame.removeNotify();// make the frame not displayable
			frame.setResizable(false);
			frame.setUndecorated(true);
			println("frame is at " + frame.getLocation());
			frame.addNotify();
		}

		super.init();
	}

	static public void main(String args[])
	{
		FileParser fp = new FileParser("mother" + ".ini");

		// parse ini file if it exists
		if (fp.fileExists())
		{
			if (fp.getIntValue("FullScreen") == 1)
			{
				int outputScreen = fp.getIntValue("outputScreen");

				GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
				GraphicsDevice devices[] = environment.getScreenDevices();
				String location;

				Rectangle virtualBounds = new Rectangle();

				String display;

				if (devices.length > outputScreen)
				{ // we have a 2nd display/projector

					GraphicsConfiguration[] gc = devices[outputScreen].getConfigurations();

					if (gc.length > 0)
						;
					{
						virtualBounds = gc[0].getBounds();// virtualBounds.union(gc[0].getBounds());
					}

					location = "--location=" + virtualBounds.x + "," + virtualBounds.y;

					display = "--display=" + (outputScreen + 1); // processing considers the first display to be # 1

					pos_X = virtualBounds.x;
					pos_Y = virtualBounds.y;
				}
				else
				{// leave on primary display
					location = "--location=0,0";

					display = "--display=" + 1; // processing considers the first display to be # 1

					pos_X = 0;
					pos_Y = 0;
				}

				PApplet.main(new String[] { location, "--hide-stop", /* display, */"Mother" });

				// PApplet.main(new String[] { "--present", "--display=3", "Mother"} );
			}
			else
			{
				PApplet.main(new String[] { "Mother" });
			}
		}

		// PApplet.main(new String[] { "--present", "Mother"} );
		// PApplet.main(new String[] { "Mother"} );
	}

	private void CallRegisteredMethods(ChildWrapper w, String fieldName)
	{
		try
		{
			Field sven;

			sven = (Field) ((Class<? extends PApplet>) w.Child().getClass().getGenericSuperclass())
					.getDeclaredField(fieldName);

			sven.setAccessible(true);

			RegisteredMethods regMethods = (RegisteredMethods) sven.get(w.Child());

			regMethods.handle();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void dealWithMessageStack()
	{
		synchronized (m_MessageStack)
		{
			Object[] args;
			OscMessage theOscMessage;
			OSCMessage m;
			for (int i = 0; i < m_MessageStack.size(); i++)
			{
				m = m_MessageStack.get(i).message;
				args = m.getArguments();

				theOscMessage = new OscMessage(m.getAddress(), args);

				oscEvent(theOscMessage);
			}

			// System.out.println("Message stack size: " + m_MessageStack.size());
			m_MessageStack.clear();
		}
	}

	private void printFrameRate()
	{
		if (!firstProfiledFrame)
		{
			if (++profiledFrameCount == 30)
			{
				long endTimeMillis = System.currentTimeMillis();
				double secs = (endTimeMillis - startTimeMillis) / 1000.0;
				double fps = 30.0 / secs;
				// double ppf = tileSize * tileSize * 2;
				// double mpps = ppf * fps / 1000000.0;
				/*
				 * System.err.println("fps: " + fps + " polys/frame: " + ppf + " million polys/sec: " + mpps +
				 * " DrawElements calls/frame: " + (numDrawElementsCalls / 30));
				 */
				// System.err.println(vboEnabled);
				profiledFrameCount = 0;
				numDrawElementsCalls = 0;
				startTimeMillis = System.currentTimeMillis();

				NumberFormat nf = NumberFormat.getInstance();

				nf.setMaximumFractionDigits(3);

				String number = nf.format(fps);

				System.out.println(number);
			}
		}
		else
		{
			startTimeMillis = System.currentTimeMillis();
			firstProfiledFrame = false;
		}
	}

	private void handleImageRecording()
	{
		if (m_WriteImage)
		{
			// sendPicWritingStartedMessage();
			// noLoop();
			saveFrame(m_ImageFolder + "Mother-#####.png");
			// loop();
			// sendNextFrameMessage();
		}
	}

	private void listenToOSC()
	{
		try
		{
			OSCPortIn receiver = new OSCPortIn(m_osc_receive_port);

			OSCListener listener = new OSCListener()
			{
				public void acceptMessage(java.util.Date time, OSCMessage message)
				{
					motherAcceptMessage(time, message);
				}
			};

			receiver.addListener("/Mother/*", listener);
			receiver.startListening();
		}
		catch (Exception e)
		{
			println("Address already in use: Cannot bind");
			System.exit(0);
		}
	}

	/**
	 * Loads the Settings from the Client INI file
	 */
	private void loadIniFile(String fileString)
	{
		fp = new FileParser(fileString);

		// parse ini file if it exists
		if (fp.fileExists())
		{
			m_IP = fp.getStringValue("IP");
			m_osc_receive_port = fp.getIntValue("osc_receive_port");
			m_osc_send_port = fp.getIntValue("osc_send_port");
			int[] localDim = fp.getIntValues("screenSize");
			m_OutputScreen = fp.getIntValue("outputScreen");

			m_Width = localDim[0];
			m_Height = localDim[1];

			m_Synth_Folder = fp.getStringValue("SynthFolder");

			if (fp.getIntValue("FullScreen") == 1)
			{
				m_FullScreen = true;
			}
			else
			{
				m_FullScreen = false;
			}

			String frameRateString = fp.getStringValue("frameRate");

			m_FrameRate = Float.parseFloat(frameRateString);
			m_ImageFolder = fp.getStringValue("imagePath");

			String speedFractionString = fp.getStringValue("speedFraction");

			m_SpeedFraction = Float.parseFloat(speedFractionString);

			if (fp.getIntValue("stereo") == 1)
			{
				m_Stereo = true;
			}
			else
			{
				m_Stereo = false;
			}
			
			if (fp.getIntValue("billboard") == 1)
			{
				m_Billboard = true;
			}
			else
			{
				m_Billboard = false;
			}
		}
	}

	/*
	 * public String getMenuTitle() { return "Mother 0.7"; }
	 * 
	 * public void init(Editor arg0) { // TODO Auto-generated method stub
	 * 
	 * }
	 */
	/*
	 * public void run() { main(null); }
	 */
}
