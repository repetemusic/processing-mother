package mother.library;
import processing.core.*; 
import processing.opengl.*;
import javax.media.opengl.*;
import foetus.Foetus;
import foetus.FoetusParameter;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 *
 */
public class ChildWrapper extends SynthContainer {
//	private Logger logger = null;
//	RenderSketchToTexture m_RenderToTexture;
	
	PApplet 		m_Child; 
	Mother  		r_Mother;		
	boolean 		m_RenderBillboard = true;
	String 			m_Name;
	Foetus 			foetusField;
	
	int				m_BlendMode = 1;
	float			m_Alpha		= 1.0f;
	
	public float 	GetAlpha() 			{ return m_Alpha; }
	public void 	SetAlpha(float a) 	{ m_Alpha = a; }
	
	public PApplet 	Child()								{ return m_Child;	}
	public boolean 	GetRenderBillboard()				{ return m_RenderBillboard; }
	public void	   	SetRenderBillboard(boolean rB)		{ m_RenderBillboard = rB; }
	public int 		GetBlendMode()						{ return m_BlendMode; }
	public void		SetBlendMode(int mode)				{ m_BlendMode = mode; }
	
	public String 	GetName() 							{ return m_Name; }
	public Foetus 	getFoetusField()					{ return foetusField; }
	public void 	setFoetusField(Foetus foetusField)	{ this.foetusField = foetusField; }

	/**
	 *  ChildWrapper CONSTRUCTOR
	 */
	public ChildWrapper(PApplet child, String name, boolean billboard, Mother mother) {	
		r_Mother				= mother;
		m_Name 					= name;
		m_RenderBillboard 		= billboard;		
//		m_Blending_Source 		= GL.GL_SRC_ALPHA;
//		m_Blending_Destination 	= GL.GL_ONE_MINUS_SRC_ALPHA;
		m_Child 				= child;	
	}
		
	/**
	 * METHODS
	 */
	
	public void draw(boolean stereo) {		
		if(m_Child.g != null) {
			if(m_RenderBillboard) {
//				if( m_RenderToTexture == null)
//				{
//					m_RenderToTexture = new RenderSketchToTexture(	r_Mother.getChildWidth(), 
//																	r_Mother.getChildHeight(), 
//																	m_Child, 
//																	r_Mother,
//																	stereo);
//				}
//				
//				
//				m_RenderToTexture.draw();				
			}
			else {
//				logger.info("Before Draw: " + m_Name);
				
				m_Child.frameCount	= r_Mother.GetParent().frameCount;
				
				ArrayList<FoetusParameter> params = this.foetusField.getParameters();
				
				for(int pi = 0; pi < params.size(); pi++) {
					params.get(pi).tick();
				}
				
				PGraphicsOpenGL pgl = (PGraphicsOpenGL) m_Child.g;
				PGL opengl 			= pgl.beginPGL();
				
				opengl.gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
				
				m_Child.g.pushMatrix();				
				m_Child.draw();
				m_Child.g.popMatrix();
				
				pgl.endPGL();
				
//				logger.info("After Draw: " + m_Name);
			}
		}
		else {
//			System.out.println("Applet thread not yet initialized, g == null");
			return;
		}
	}		
}