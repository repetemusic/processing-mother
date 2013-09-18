package mother.library;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import oscP5.OscMessage;
import processing.core.PApplet;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

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
					Add_synth(theOscMessage);			
				else if (splits[2].compareTo("Reset") == 0)
					Reset();
				else if (splits[2].compareTo("Remove_synth") == 0)
					RemoveSynth(theOscMessage);
				else if (splits[2].compareTo("Move_synth") == 0)
					MoveSynth(theOscMessage);
				else if (splits[2].compareTo("Add_ChildSynth") == 0)
					Add_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Remove_ChildSynth") == 0)
					Remove_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Move_ChildSynth") == 0)
					Move_ChildSynth(theOscMessage);
				else if (splits[2].compareTo("Set_synth_blending") == 0)
					SetSynthBlending(theOscMessage);
				else if (splits[2].compareTo("Child") == 0 && splits.length >= 4)
					Child(theOscMessage, splits);
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
	
	private void Add_synth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("ss")) {
			if (!r_M.GetSynthContainer().contains(theOscMessage.get(1).stringValue()))	{
				r_M.GetParent().noLoop();

				ChildWrapper wrapper = r_M.GetSynthContainer().Add(	theOscMessage.get(1).stringValue(), 
																	theOscMessage.get(0).stringValue(),
																	r_M.GetSynthLoader(),
																	r_M);

				if(wrapper!=null) {
					r_M.sendSupportedMessages(wrapper);
				}

				r_M.GetParent().loop();
			}
		}
	}
	
	private void RemoveSynth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("s")) {
			ChildWrapper w = r_M.GetSynthContainer().Remove(theOscMessage.get(0).stringValue());

			r_M.callRegisteredMethod(w, "dispose");
		}
	}
	
	private void MoveSynth(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("si")) {
			r_M.GetSynthContainer().Move(theOscMessage.get(0).stringValue(), theOscMessage.get(1).intValue());
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
				r_M.sendSupportedMessages(wrapper);
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
			ChildWrapper wrapper 		= null;
			
			if(parentWrapper!=null) {
				if (parentWrapper.contains(theOscMessage.get(1).stringValue()))	{
					parentWrapper.Move(theOscMessage.get(1).stringValue(), theOscMessage.get(2).intValue());
				}
			}
		}
	}
	
	private void Child(OscMessage theOscMessage, String[] splits) {
		StringBuffer newAddrPattern = new StringBuffer();
		String childName;

		for (int pos = 4; pos < splits.length; pos++) {
			newAddrPattern.append("/" + splits[pos]);
		}

		PApplet child;
		Method oscEventMethod;
		
		for (int i = 0; i < r_M.GetSynthContainer().Synths().size(); i++) {
			child 		= ((ChildWrapper) r_M.GetSynthContainer().Synths().get(i)).Child();
			childName 	= ((ChildWrapper) r_M.GetSynthContainer().Synths().get(i)).GetName();

			if (childName.compareTo(splits[3]) == 0) {
				if (splits[4].compareTo("Get_Supported_Messages") == 0)	{
					r_M.sendSupportedMessages((ChildWrapper) r_M.GetSynthContainer().Synths().get(i));
				}
				else {
				// Handling messages to synths
					try	{
						// removing "/Mother/Child/Synth_Name" from address pattern
						theOscMessage.setAddrPattern(newAddrPattern.toString());

						oscEventMethod = child.getClass().getDeclaredMethod("oscEvent",
								new Class[] { OscMessage.class });

						oscEventMethod.invoke(child, new Object[] { theOscMessage });
					}
					catch (Exception e)	{
						r_M.GetParent().println("CRASH Child oscEvent" + childName + e.getStackTrace());
						r_M.GetParent().println(e.getStackTrace());
					}
				}

				break;
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
	
	private void SetSynthBlending(OscMessage theOscMessage) {
		if (theOscMessage.checkTypetag("sii")) {
			r_M.GetSynthContainer().Set_Synth_Blending(theOscMessage.get(0).stringValue(), theOscMessage.get(1)
					.intValue(), theOscMessage.get(2).intValue());
		}
	}
	
	private void Reset() {
		r_M.GetSynthContainer().reset();
	}
}
