package mother.library;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import oscP5.OscMessage;
import processing.core.PApplet;
import processing.core.PConstants;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import foetus.Foetus;

public class Operations
{	
	Mother r_M;
	
	public Operations(Mother m) {
		r_M = m;
	}

	public void oscEvent(OscMessage theOscMessage, String[] splits) {
		if (splits.length >= 2 && (splits[1].compareTo("Mother") == 0))	{			
				if (splits[2].compareTo("Get_synth_names") == 0)
					Get_Synth_Names();
				else if (splits[2].compareTo("Add_synth") == 0)
					Add_synth(theOscMessage, r_M.GetSynthContainer());			
				else if (splits[2].compareTo("Reset") == 0)
					Reset();
				else if (splits[2].compareTo("Remove_synth") == 0)
					RemoveSynth(theOscMessage, r_M.GetSynthContainer());
				else if (splits[2].compareTo("Move_synth") == 0)
					MoveSynth(theOscMessage, r_M.GetSynthContainer());
				else if (splits[2].compareTo("Add_ChildSynth") == 0)
					Add_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Remove_ChildSynth") == 0)
					Remove_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Move_ChildSynth") == 0)
					Move_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Child") == 0 && splits.length >= 4)
					Child(theOscMessage, splits, r_M.GetSynthContainer(), 0);
				else if (splits[2].compareTo("Record") == 0)
					Record(theOscMessage);
		}
		else {
		// Message not for mother
			// println("Unhandled OSC message: " + theOscMessage.addrPattern());
		}
	}
	
	private void Get_Synth_Names() {
		OSCPortOut sender;
		
		try	{
			InetAddress ip = InetAddress.getByName(r_M.GetIP());
			ArrayList<String> list = new ArrayList<String>();
			sender = new OSCPortOut(ip, r_M.GetOSCSendPort());
			
			for (Enumeration<String> e = r_M.GetSynthLoader().get_Synth_Names().keys(); e.hasMoreElements();) {
				list.add(e.nextElement());
			}

			Object args[] = new Object[list.size()];

			for (int i = 0; i < list.size(); i++) {
				args[i] = list.get(i);
			}

			sender.send(new OSCMessage("/Synth_names", args));
		}
		catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		catch (SocketException e1) {
			e1.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void Add_synth(OscMessage theOscMessage, SynthContainer scIn) {
		if (theOscMessage.checkTypetag("ss")) {
			if (!scIn.contains(theOscMessage.get(1).stringValue()))	{
				r_M.GetParent().noLoop();

				ChildWrapper wrapper = scIn.Add(	theOscMessage.get(1).stringValue(), 
													theOscMessage.get(0).stringValue(),
													r_M.GetSynthLoader(),
													r_M);

				if(wrapper!=null) {
					sendSupportedMessages(wrapper);
				}

				r_M.GetParent().loop();
			}
		}
	}
	
	private void RemoveSynth(OscMessage theOscMessage, SynthContainer scIn) {
		if (theOscMessage.checkTypetag("s")) {
			ChildWrapper w = scIn.Remove(theOscMessage.get(0).stringValue());

			r_M.callRegisteredMethod(w, "dispose");
		}
	}
	
	private void MoveSynth(OscMessage theOscMessage, SynthContainer scIn) {
		if (theOscMessage.checkTypetag("si")) {
			scIn.Move(theOscMessage.get(0).stringValue(), theOscMessage.get(1).intValue());
		}
	}
	
	private void Child(OscMessage theOscMessage, String[] splits, SynthContainer scIn, int splitsDepth) {
		String 			childName 			= null;
		PApplet 		child 				= null;
		Method 			oscEventMethod 		= null;
		ChildWrapper	currentChildWrapper	= null;
		String 			destinationName;
		
		for (int i = 0; i < scIn.Synths().size(); i++) {
			currentChildWrapper = (ChildWrapper) scIn.Synths().get(i); 
			child 				= currentChildWrapper.Child();
			childName 			= currentChildWrapper.GetName();
			destinationName 	= splits[3-splitsDepth];
			
			if (childName.compareTo(destinationName) == 0) {
				if (splits[4-splitsDepth].compareTo("Get_Supported_Messages") == 0)
					sendSupportedMessages((ChildWrapper) scIn.Synths().get(i));
				else if (splits[4-splitsDepth].compareTo("Add_synth") == 0)
					Add_synth(theOscMessage, currentChildWrapper);	
				else if (splits[4-splitsDepth].compareTo("Remove_synth") == 0)
					RemoveSynth(theOscMessage, currentChildWrapper);
				else if (splits[4-splitsDepth].compareTo("Move_synth") == 0)
					MoveSynth(theOscMessage, currentChildWrapper);
				else if (splits[4-splitsDepth].compareTo("Set_BlendMode") == 0) {
					SetBlendMode(theOscMessage, currentChildWrapper);
				}
				else if (splits[4-splitsDepth].compareTo("Set_Alpha") == 0) {
					SetAlpha(theOscMessage, currentChildWrapper);
				}
				else {		
					// Handling messages to synths
					try	{
						StringBuffer newAddrPattern = new StringBuffer();
						
						/*
						 * Building a new AP, removing the parent.
						 */
						if(splitsDepth==0) {
							// removing "/Mother/Child/Synth_Name" from address pattern
							for (int pos = 4; pos < splits.length; pos++) {
								newAddrPattern.append("/" + splits[pos]);
							}
						}
						else {
							for (int pos = 4-splitsDepth; pos < splits.length; pos++) {
								newAddrPattern.append("/" + splits[pos]);
							}
						}

						String[] newSplits = newAddrPattern.toString().split("/"); 
						
						theOscMessage.setAddrPattern(newAddrPattern.toString());
						
						if(newSplits.length == 2) {
							oscEventMethod = child.getClass().getDeclaredMethod("oscEvent",
									new Class[] { OscMessage.class });
	
							oscEventMethod.invoke(child, new Object[] { theOscMessage });
						}
						else {							
							Child(theOscMessage, newAddrPattern.toString().split("/"), currentChildWrapper, 2);
						}
					}
					catch (Exception e)	{
						PApplet.println("CRASH Child oscEvent" + childName + e.getStackTrace());
						PApplet.println(e.getStackTrace());
					}
				}

				break;
			}
		}
	}
	
	private void Add_ChildSynth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("sss")) {
			String parentSynthID = theOscMessage.get(0).stringValue();
			
			r_M.GetParent().noLoop();

			ChildWrapper parentWrapper 	= r_M.GetSynthContainer().GetChildWrapper(parentSynthID);
			ChildWrapper wrapper 		= null;
			
			if(parentWrapper!=null) {
				if (!parentWrapper.contains(theOscMessage.get(2).stringValue()))	{
					wrapper = parentWrapper.Add(	theOscMessage.get(2).stringValue(), 
													theOscMessage.get(1).stringValue(),
													r_M.GetSynthLoader(),
													r_M);
				}
			}
				
			if(wrapper!=null) {
				sendSupportedMessages(wrapper);
			}

			r_M.GetParent().loop();
		}
	}
	
	private void Remove_ChildSynth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("ss")) {
			String parentSynthID = theOscMessage.get(0).stringValue();

			ChildWrapper parentWrapper 	= r_M.GetSynthContainer().GetChildWrapper(parentSynthID);
			ChildWrapper wrapper 		= null;
			
			if(parentWrapper!=null) {
				if (parentWrapper.contains(theOscMessage.get(1).stringValue()))	{
					wrapper = parentWrapper.Remove(theOscMessage.get(1).stringValue());

					r_M.callRegisteredMethod(wrapper, "dispose");
				}
			}
		}
	}
	
	private void Move_ChildSynth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("ssi")) {
			String parentSynthID = theOscMessage.get(0).stringValue();

			ChildWrapper parentWrapper 	= r_M.GetSynthContainer().GetChildWrapper(parentSynthID);
		
			if(parentWrapper!=null) {
				if (parentWrapper.contains(theOscMessage.get(1).stringValue()))	{
					parentWrapper.Move(theOscMessage.get(1).stringValue(), theOscMessage.get(2).intValue());
				}
			}
		}
	}
	
	private void Record(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("i")) {
			int in = theOscMessage.get(0).intValue();

			if (in == 1) {
				r_M.SetWriteImage(true);
				System.out.println("Recording!");
			}
			else if (in == 0) {
				r_M.SetWriteImage(false);
				System.out.println("Stopped Recording!");
			}
		}
	}
	
	private void SetBlendMode(OscMessage theOscMessage, ChildWrapper in) {
		if (theOscMessage.checkTypetag("i")) {
			
			/*
			 *	BLEND - linear interpolation of colours: C = A*factor + B. This is the default blending mode.
			 *	ADD - additive blending with white clip: C = min(A*factor + B, 255)
			 *	SUBTRACT - subtractive blending with black clip: C = max(B - A*factor, 0)
			 *	DARKEST - only the darkest colour succeeds: C = min(A*factor, B)
			 *	LIGHTEST - only the lightest colour succeeds: C = max(A*factor, B)
			 *	DIFFERENCE - subtract colors from underlying image.
			 *	EXCLUSION - similar to DIFFERENCE, but less extreme.
			 *	MULTIPLY - multiply the colors, result will always be darker.
			 *	SCREEN - opposite multiply, uses inverse values of the colors.
			 *	REPLACE - the pixels entirely replace the others and don't utilize alpha (transparency) values 
			 */
			
			int mode = 1;
			
			int value = theOscMessage.get(0).intValue();
			
			if( value == 1)
				mode = PConstants.BLEND;
			else if(value == 2)
				mode = PConstants.ADD;
			else if(value == 3)
				mode = PConstants.SUBTRACT;
			else if(value == 4)
				mode = PConstants.DARKEST;
			else if(value == 5)
				mode = PConstants.LIGHTEST;
			else if(value == 6)
				mode = PConstants.DIFFERENCE;
			else if(value == 7)
				mode = PConstants.EXCLUSION;
			else if(value == 8)
				mode = PConstants.MULTIPLY;
			else if(value == 9)
				mode = PConstants.SCREEN;
			else if(value == 10)
				mode = PConstants.REPLACE;
			
			in.SetBlendMode(mode);
		}
	}
	
	private void SetAlpha(OscMessage theOscMessage, ChildWrapper in) {
		if (theOscMessage.checkTypetag("f")) {
			in.SetAlpha(theOscMessage.get(0).floatValue());
		}
	}
		
	private void Reset() {
		r_M.GetSynthContainer().reset();
	}
	
	protected void sendSupportedMessages(ChildWrapper wrapper) {
		PApplet child = wrapper.Child();
		String childName = wrapper.GetName();

		Foetus f = null;

		try {
			f = (Foetus) child.getClass().getField("f").get(child);
		}
		catch (Exception e) {
			PApplet.println("CRASH: Accessing child's foetus failed!" + e.getMessage());
		}

		Hashtable<String, String> supportedMessages = f.getSupportedMessages();
		Enumeration<String> e = supportedMessages.elements();

		OSCPortOut sender;
		
		try {
			// (m_IP, m_osc_send_port)
			InetAddress ip = InetAddress.getByName(r_M.GetIP());
			sender = new OSCPortOut(ip, r_M.GetOSCSendPort());

			ArrayList<String> list = new ArrayList<String>();
			for (Enumeration<String> ek = supportedMessages.keys(); ek.hasMoreElements();) {
				list.add(ek.nextElement());
				list.add(e.nextElement());
			}

			Object args[] = new Object[list.size()];

			for (int i = 0; i < list.size(); i++) {
				args[i] = list.get(i);
			}

			OSCMessage msg = new OSCMessage("/Synth_supported_messages/" + childName, args);

			sender.send(msg);
		}
		catch (UnknownHostException e1)	{
			e1.printStackTrace();
		}
		catch (SocketException e1) {
			e1.printStackTrace();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
