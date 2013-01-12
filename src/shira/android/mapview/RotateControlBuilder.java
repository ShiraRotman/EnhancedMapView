package shira.android.mapview;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.*;
//import android.util.FloatMath;
//import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

class DirectionPadHandle extends ViewGroup
{
	static final float PAD_RADIUS_RATIO=0.65f;
	private static final float HANDLE_EDGE_RADIUS_RATIO=0.2f;
	private static final float DIRECTION_HANDLE_RATIO=0.1f;
	static final float DIRECTION_CIRCLE_RATIO=1-DIRECTION_HANDLE_RATIO*2;
	/*I want to start from the "up" direction, but because the y-axis 
	 *of the screen goes down, I have to subtract*/
	static final float DEF_INIT_HANDLE_ANGLE=-90;
	
	private static final int DIRECTION_HANDLE_COLOR=0xFFE0E0E0;
	private static final int DIRECTION_HANDLE_OPERATED_COLOR=0xFF9BC3FF;
	private static final int CIRCLE_STROKE_COLOR=0xD0E0E0E0;
	private static final int CIRCLE_FILL_COLOR=0x80E0E0E0;
	private static final int CIRCLE_STROKE_WIDTH=2;
	
	private static final Paint circleStrokePaint,circleFillPaint;
	//private static final Matrix rotationMatrix=new Matrix();
	
	private DirectionPad directionPad;
	private List<DirectionPadHandleListener> directionHandleListeners;
	private Paint handlePaint=new Paint();
	private RectF circleBounds=new RectF(),handleBounds=new RectF();
	private RectF invalidationBoundsF=new RectF();
	private Rect invalidationBounds=new Rect();
	private double tempHandleAngleRadians;
	private float radius,handleWidth,handleHeight; //,handleAngle;
	private int pointerID=-1;
	
	public static interface DirectionPadHandleListener extends 
			DirectionPad.DirectionPadListener
	{
		public abstract void handleOperated(DirectionPadHandle directionPadHandle,
				float handleAngle);
	}
	
	static
	{
		circleStrokePaint=new Paint();
		circleStrokePaint.setStyle(Paint.Style.STROKE);
		circleStrokePaint.setStrokeWidth(CIRCLE_STROKE_WIDTH);
		circleStrokePaint.setColor(CIRCLE_STROKE_COLOR);
		circleFillPaint=new Paint();
		circleFillPaint.setColor(CIRCLE_FILL_COLOR);
	}
	
	public DirectionPadHandle(Context context)
	{ this(context,DEF_INIT_HANDLE_ANGLE); }
	
	public DirectionPadHandle(Context context,float handleAngle) 
	{ 
		super(context);
		directionHandleListeners=new LinkedList<DirectionPadHandleListener>();
		setHandleAngle(handleAngle);
		directionPad=new DirectionPad(context);
		addView(directionPad);
		setWillNotDraw(false);
	}
	
	public void setHandleAngle(float handleAngle)
	{
		handleAngle%=360; if (handleAngle<0) handleAngle+=360;
		double handleAngleRadians=handleAngle/180*Math.PI;
		/*If the handle's rectangle does not occupy any space, then that means 
		 *it has never been measured, so the all the size measures may also have 
		 *not been calculated (onSizeChanged was never called), and because they 
		 *are needed to determine the rectangle, I can't update the handle's 
		 *angle (which of course affects its bounds) just yet, and have to 
		 *postpone it until the measures are known.*/
		if (!handleBounds.isEmpty())
			updateHandleAngle(handleAngleRadians);
		else tempHandleAngleRadians=handleAngleRadians;
	}
	
	public void addListener(DirectionPadHandleListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to register must be " +
					"non-null!");
		directionPad.addListener(listener);
		directionHandleListeners.add(listener);
	}
	
	public void removeListener(DirectionPadHandleListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to unregister must " +
					"be non-null!");
		directionPad.removeListener(listener);
		directionHandleListeners.remove(listener);
	}
	
	@Override 
	protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight)
	{
		super.onSizeChanged(width,height,oldWidth,oldHeight);
		float circleWidth=width*DIRECTION_CIRCLE_RATIO;
		float circleHeight=height*DIRECTION_CIRCLE_RATIO;
		circleBounds.left=(width-circleWidth)/2; 
		circleBounds.top=(height-circleHeight)/2;
		circleBounds.right=circleBounds.left+circleWidth;
		circleBounds.bottom=circleBounds.top+circleHeight;
		radius=circleWidth/2;
		handleWidth=width*DIRECTION_HANDLE_RATIO;
		handleHeight=height*DIRECTION_HANDLE_RATIO;
		/*If that's the first time this function is called and the measures are 
		 *determined, I have to also process the initial angle of the handle and 
		 *calculate its rectangle (see setHandleAngle)*/
		if (handleBounds.isEmpty())
		{
			updateHandleAngle(tempHandleAngleRadians);
			handlePaint.setColor(DIRECTION_HANDLE_COLOR);
		}
	}
	
	@Override
	protected void onLayout(boolean changed,int left,int top,int right,int bottom)
	{
		int childWidth=Math.round(circleBounds.width()*PAD_RADIUS_RATIO);
		int childHeight=Math.round(circleBounds.height()*PAD_RADIUS_RATIO);
		/*Log.i("MapView","Circle: " + circleBounds.width() + "," + circleBounds.
				height());*/
		//Log.i("MapView","Bounds: " + (right-left) + "," + (bottom-top));
		int childLeft=Math.round((right-left-childWidth)/2);
		int childTop=Math.round((bottom-top-childHeight)/2);
		//Log.i("MapView","Pad: " + childLeft + "," + childTop);
		directionPad.layout(childLeft,childTop,childLeft+childWidth,childTop+
				childHeight);
	}
	
	@Override protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		//Log.i("MapView","Now Drawing: " + circleBounds);
		canvas.drawOval(circleBounds,circleFillPaint);
		canvas.drawOval(circleBounds,circleStrokePaint);
		canvas.drawRoundRect(handleBounds,handleBounds.width()*
				HANDLE_EDGE_RADIUS_RATIO,handleBounds.height()*
				HANDLE_EDGE_RADIUS_RATIO,handlePaint);
	}
	
	@Override public boolean onTouchEvent(MotionEvent event)
	{
		switch (event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				pointerID=event.getPointerId(0);
				handlePaint.setColor(DIRECTION_HANDLE_OPERATED_COLOR);
			case MotionEvent.ACTION_MOVE:
				int pointerIndex=event.findPointerIndex(pointerID);
				if (pointerIndex>-1)
				{
					float positionX=event.getX(pointerIndex);
					float positionY=event.getY(pointerIndex);
					/*if ((circleBounds.contains(positionX,positionY))||(handleBounds.
							contains(positionX,positionY)))
					{*/
					float differenceX=positionX-circleBounds.centerX();
					float differenceY=positionY-circleBounds.centerY();
					double handleAngleRadians=Math.atan(differenceY/differenceX);
					//Log.i("MapView","Handle angle1: " + handleAngleRadians);
					/*Arctan returns only values in [-PI/2,PI/2], so I need to 
					 *adjust the result to the negative cos half of the circle*/
					if (differenceX<0) handleAngleRadians+=Math.PI;
					//Log.i("MapView","Handle angle2: " + handleAngleRadians);
					updateHandleAngle(handleAngleRadians);
					float handleAngle=(float)(handleAngleRadians/Math.PI*180);
					for (DirectionPadHandleListener listener:directionHandleListeners)
						listener.handleOperated(this,handleAngle);
					return true;
					/*}
					else return false;*/
				}
				else return false;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (pointerID>-1)
				{
					pointerID=-1;
					handlePaint.setColor(DIRECTION_HANDLE_COLOR);
					handleBounds.roundOut(invalidationBounds);
					invalidate(invalidationBounds);
					return true;
				}
				else return false;
			default: return (pointerID>-1);
		}
	}
	
	private void updateHandleAngle(double handleAngleRadians)
	{
		float handlePointX=(float)(radius*Math.cos(handleAngleRadians))+
				circleBounds.centerX();
		float handlePointY=(float)(radius*Math.sin(handleAngleRadians))+
				circleBounds.centerY();
		//Log.i("MapView","Handle point: " + handlePointX + "," + handlePointY);
		invalidationBoundsF.set(handleBounds);
		handleBounds.left=handlePointX-handleWidth/2;
		handleBounds.top=handlePointY-handleHeight/2;
		handleBounds.right=handlePointX+handleWidth/2;
		handleBounds.bottom=handlePointY+handleHeight/2;
		/*handleAngle=(float)(handleAngleRadians/Math.PI*180);
		rotationMatrix.setRotate(handleAngle);
		rotationMatrix.mapRect(handleBounds);*/
		invalidationBoundsF.union(handleBounds);
		invalidationBoundsF.roundOut(invalidationBounds);
		invalidate(invalidationBounds);
	}
}

class RotateControlBuilder implements MapControlBuilder
{
	private static final int CONTROL_WIDTH=(int)(PanControlBuilder.CONTROL_WIDTH/
			(DirectionPadHandle.DIRECTION_CIRCLE_RATIO*DirectionPadHandle.
			PAD_RADIUS_RATIO));
	private static final int CONTROL_HEIGHT=(int)(PanControlBuilder.CONTROL_HEIGHT/
			(DirectionPadHandle.DIRECTION_CIRCLE_RATIO*DirectionPadHandle.
			PAD_RADIUS_RATIO));
	private static final float ROTATION_AMOUNT=2f;
	
	private static int controlWidth;
	private static int controlHeight;
	
	private class DirectionPadHandleOperateListener implements DirectionPadHandle.
			DirectionPadHandleListener
	{
		private MapRotationController rotationController;
		
		public DirectionPadHandleOperateListener(EnhancedMapView mapView)
		{ rotationController=mapView.getRotationController(); }
		
		@Override
		public void directionOperated(DirectionPad directionPad,int directionX,
				int directionY) 
		{
			if (directionX!=0)
			{
				float rotationDegrees=rotationController.getMapRotation()+
						directionX*ROTATION_AMOUNT;
				rotationController.animateRotation(rotationDegrees,(directionX>0),
						null,100);
			}
		}
		
		@Override
		public void handleOperated(DirectionPadHandle directionPadHandle,
				float handleAngle) 
		{ rotationController.setMapRotation(handleAngle+90); }
	}
	
	@Override
	public View buildControl(Map<String,Object> properties,View existingControl,
			EnhancedMapView mapView)
	{
		if (existingControl instanceof DirectionPadHandle) 
			return existingControl;
		else
		{
			float rotationAngle=(mapView!=null?mapView.getRotationController().
					getMapRotation()-90:DirectionPadHandle.DEF_INIT_HANDLE_ANGLE);
			return new DirectionPadHandle(MapControlDefsUtils.extractContext(
					existingControl,mapView),rotationAngle);
		}
	}
	
	@Override 
	public void registerListeners(final EnhancedMapView mapView,View control) 
	{ 
		if (!(control instanceof DirectionPadHandle)) return;
		final DirectionPadHandle directionPadHandle=(DirectionPadHandle)control;
		if (control.getTag(R.id.control_listener)==null)
		{
			DirectionPadHandle.DirectionPadHandleListener controlListener=new 
					DirectionPadHandleOperateListener(mapView);
			directionPadHandle.addListener(controlListener);
			directionPadHandle.setTag(R.id.control_listener,controlListener);
		}
		if (control.getTag(R.id.change_listener)==null)
		{
			MapViewChangeListener changeListener=new AbstractMapViewChangeListener() 
			{	
				@Override
				public void onRotate(EnhancedMapView mapView,float oldDegrees,
						float newDegrees) 
				{ directionPadHandle.setHandleAngle(newDegrees-90); }
			};
			mapView.addChangeListener(changeListener);
			directionPadHandle.setTag(R.id.change_listener,changeListener);
		}
	}
	
	@Override public int getMinimumWidth(Map<String,Object> properties)
	{
		if (controlWidth==0)
		{
			controlWidth=Math.round(CONTROL_WIDTH*MapControlDefsUtils.
					densityFactor);
		}
		return controlWidth;
	}
	
	@Override public int getMinimumHeight(Map<String,Object> properties)
	{
		if (controlHeight==0)
		{
			controlHeight=Math.round(CONTROL_HEIGHT*MapControlDefsUtils.
					densityFactor);
		}
		return controlHeight;
	}
	
	@Override public EnhancedMapView.ControlAlignment getDefaultAlignment()
	{ return EnhancedMapView.ControlAlignment.TOP_LEFT; }
}
