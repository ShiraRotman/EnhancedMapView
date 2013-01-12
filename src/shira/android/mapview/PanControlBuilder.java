package shira.android.mapview;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.google.android.maps.MapController;

class DirectionPad extends View
{
	/*public static final int NO_DIRECTION=0;
	public static final int DIRECTION_LEFT=1; //0001
	public static final int DIRECTION_RIGHT=2; //0010
	public static final int DIRECTION_UP=4; //0100
	public static final int DIRECTION_DOWN=8; //1000
	
	public static final int HORIZONTAL_DIRECTION_MASK=3; //0011
	public static final int VERTICAL_DIRECTION_MASK=12; //1100*/
	
	public static final long TOUCH_REPEAT_TIME_ELAPSE=41;
	private static final double DIRECTION_THRESHOLD=0.15;
	
	private static final int ARROW_COLOR=0xFF5D86BF;
	/*private static final int TOUCH_START_COLOR=0x000000FF;
	private static final int TOUCH_END_COLOR=0xFF0000FF;*/
	private static final int TOUCH_AREA_COLOR=0xFF9BC3FF;
	
	private static final float ARROW_LINE_WIDTH=3;
	private static final float ARROW_START_PERCENT=0.1f;
	private static final float ARROW_END_PERCENT=0.2f;
	private static final float ARROW_ANGLE=(float)Math.PI/4;
	private static final float ARROW_SLOPE=FloatMath.sin(ARROW_ANGLE)/
			FloatMath.cos(ARROW_ANGLE);
	private static final float TOUCH_AREA_ARC_ANGLE=90f;
	private static final float HALF_ARC_ANGLE=TOUCH_AREA_ARC_ANGLE/2;
	
	private static final int[] directionPadColors=
	{ 0xFFD8D8D8,0xFFB0B0B0,0xFFD8D8D8,0xFFE0E0E0 };
	
	/*private static final float[] padColorsPositions=
	{ 0.125f,0.375f,0.625f,0.875f };*/ 
	
	private static final Paint directionArrowPaint,touchAreaPaint;
	//private static final Matrix gradientMatrix=new Matrix();
	
	private List<DirectionPadListener> directionListeners;
	private DirectionOperateCallback directionCallback;
	private Handler handler=new Handler(Looper.getMainLooper());
	private Paint directionPadPaint=new Paint();
	private SweepGradient directionPadGradient;
	//private LinearGradient touchAreaGradient;
	private RectF directionPadBounds=new RectF();
	private float[] directionArrowsPoints=new float[32];
	private float touchPointAngle;
	private int directionX,directionY;
	private int pointerID=-1;
	
	public static interface DirectionPadListener
	{
		public abstract void directionOperateStarted(DirectionPad directionPad,
				int directionX,int directionY);
		public abstract void directionOperated(DirectionPad directionPad,
				int directionX,int directionY);
		public abstract void directionOperateEnded(DirectionPad directionPad);
	}
	
	private class DirectionOperateCallback implements Runnable
	{
		private boolean stopped=true;
		
		public boolean stopped() { return stopped; }
		public void stop() { stopped=true; }
		public void reset() { stopped=false; } 
		
		@Override public void run()
		{
			if (!stopped)
			{
				for (DirectionPadListener listener:directionListeners)
				{
					listener.directionOperated(DirectionPad.this,directionX,
							directionY);
				}
				reset();
				handler.postDelayed(this,TOUCH_REPEAT_TIME_ELAPSE);
			}
		}
	}
	
	static
	{
		directionArrowPaint=new Paint();
		directionArrowPaint.setColor(ARROW_COLOR);
		directionArrowPaint.setStyle(Paint.Style.STROKE);
		directionArrowPaint.setStrokeCap(Paint.Cap.SQUARE);
		directionArrowPaint.setStrokeWidth(ARROW_LINE_WIDTH*MapControlDefsUtils.
				densityFactor);
		touchAreaPaint=new Paint(); 
		touchAreaPaint.setColor(TOUCH_AREA_COLOR);
	}
	
	public DirectionPad(Context context) 
	{ 
		super(context);
		directionListeners=new LinkedList<DirectionPadListener>();
		directionCallback=new DirectionOperateCallback();
		
	}
	
	public void addListener(DirectionPadListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to register must be " +
					"non-null!");
		directionListeners.add(listener);
	}
	
	public void removeListener(DirectionPadListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to unregister must " +
					"be non-null");
		directionListeners.remove(listener);
	}
	
	/*public static int getHorizontalDirectionMasked(int direction)
	{ return direction & HORIZONTAL_DIRECTION_MASK; }
	
	public static int getVerticalDirectionMasked(int direction)
	{ return direction & VERTICAL_DIRECTION_MASK; }*/
	
	@Override 
	protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight)
	{
		super.onSizeChanged(width,height,oldWidth,oldHeight);
		float middlePointX=width/2,middlePointY=height/2;
		directionPadGradient=new SweepGradient(middlePointX,middlePointY,
				directionPadColors,null);
		directionPadPaint.setShader(directionPadGradient);
		/*touchAreaGradient=new LinearGradient(0,0,width,height,TOUCH_START_COLOR,
				TOUCH_END_COLOR,Shader.TileMode.CLAMP);
		gradientMatrix.setRotate(90,middlePointX,middlePointY);
		touchAreaGradient.setLocalMatrix(gradientMatrix);
		touchAreaPaint.setShader(touchAreaGradient);*/
		calcDirectionArrowsCoords(width,height,directionArrowsPoints);
		directionPadBounds.right=width; directionPadBounds.bottom=height;
	}
	
	@Override protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.rotate(-30,directionPadBounds.centerX(),directionPadBounds.
				centerY());
		canvas.drawOval(directionPadBounds,directionPadPaint);
		canvas.restore();
		//if (pointerID>-1)
		/*I could use the pointer ID to detect if the user is operating the 
		 *control, but it's only relevant for touches, and I want to leave the 
		 *option to support more input devices in the future*/
		if (!directionCallback.stopped())
		{
			float startAngle=touchPointAngle-HALF_ARC_ANGLE;
			canvas.drawArc(directionPadBounds,startAngle,TOUCH_AREA_ARC_ANGLE,
					true,touchAreaPaint);
		}
		canvas.drawLines(directionArrowsPoints,directionArrowPaint);
	}
	
	@Override public boolean onTouchEvent(MotionEvent event)
	{
		int action=event.getActionMasked();
		/*In the future, to support more input devices, the responses to actions 
		 *will have to be moved to dedicated functions which will be called from 
		 *each of the methods that handle input from the various devices*/
		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
				pointerID=event.getPointerId(0);
				calcTouchAngleDirections(event);
				invalidate();
				for (DirectionPadListener listener:directionListeners)
					listener.directionOperateStarted(this,directionX,directionY);
				directionCallback.reset();
				handler.postDelayed(directionCallback,TOUCH_REPEAT_TIME_ELAPSE);
				break;
			case MotionEvent.ACTION_MOVE:
				calcTouchAngleDirections(event);
				invalidate();
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				directionCallback.stop();
				handler.removeCallbacks(directionCallback);
				pointerID=-1;
				invalidate();
				for (DirectionPadListener listener:directionListeners)
					listener.directionOperateEnded(this);
				break;
		}
		return true;
	}
	
	private void calcTouchAngleDirections(MotionEvent event)
	{
		int pointerIndex=event.findPointerIndex(pointerID);
		if (pointerIndex>-1)
		{
			float differenceX=event.getX(pointerIndex)-directionPadBounds.centerX();
			float differenceY=event.getY(pointerIndex)-directionPadBounds.centerY();
			Log.i("MapView","Difference: " + differenceX + "," + differenceY);
			double angle=Math.atan(differenceY/differenceX);
			if (differenceX<0) angle+=Math.PI;
			Log.i("MapView","Radians: " + angle);
			//Could also calculate sin,cos
			double radius=Math.sqrt(Math.pow(differenceX,2)+Math.pow(differenceY,2));
			if (Math.abs(differenceX/radius)<DIRECTION_THRESHOLD) differenceX=0;
			if (Math.abs(differenceY/radius)<DIRECTION_THRESHOLD) differenceY=0;
			touchPointAngle=(float)(angle/Math.PI*180);
			//if (touchPointAngle<0) touchPointAngle+=360;
			Log.i("MapView","Angle: " + touchPointAngle);
			directionX=(int)Math.signum(differenceX);
			directionY=(int)Math.signum(differenceY);
		}
	}
	
	private static void calcDirectionArrowsCoords(int width,int height,
			float[] points)
	{
		float middlePointX=width/2,middlePointY=height/2;
		float startPointX,startPointY,endPointX,endPointY;
		int startLineIndex=0;
		
		//Up and down
		startPointX=middlePointX; startPointY=height*ARROW_START_PERCENT;
		endPointY=height*ARROW_END_PERCENT;
		endPointX=(endPointY-startPointY)/ARROW_SLOPE+startPointX;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		startPointY=height-startPointY; endPointY=height-endPointY;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		endPointX=(endPointY-startPointY)/ARROW_SLOPE+startPointX;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		startPointY=height-startPointY; endPointY=height-endPointY;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		//Left and right
		startPointX=width*ARROW_START_PERCENT; startPointY=middlePointY;
		endPointX=width*ARROW_END_PERCENT; 
		endPointY=(endPointX-startPointX)*-ARROW_SLOPE+startPointY;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		startPointX=width-startPointX; endPointX=width-endPointX;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		endPointY=(endPointX-startPointX)*-ARROW_SLOPE+startPointY;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
		startLineIndex+=4;
		
		startPointX=width-startPointX; endPointX=width-endPointX;
		points[startLineIndex]=startPointX; points[startLineIndex+1]=startPointY;
		points[startLineIndex+2]=endPointX; points[startLineIndex+3]=endPointY;
	}
}

class PanControlBuilder implements MapControlBuilder
{
	private static final long PANNING_ANIMATION_DURATION=DirectionPad.
			TOUCH_REPEAT_TIME_ELAPSE;
	
	static final int CONTROL_WIDTH=50;
	static final int CONTROL_HEIGHT=CONTROL_WIDTH;
	private static final float PAN_AMOUNT=3f;
	
	private static int controlWidth;
	private static int controlHeight;
	private static int panAmount;
	
	private ValueAnimator panningAnimator;
	private float lastAnimatedXValue,lastAnimatedYValue;
	
	@Override
	public View buildControl(Map<String,Object> properties,View existingControl,
			EnhancedMapView mapView)
	{
		if (existingControl instanceof DirectionPad) return existingControl;
		else
		{
			return new DirectionPad(MapControlDefsUtils.extractContext(
					existingControl,mapView));
		}
	}
	
	@Override 
	public void registerListeners(final EnhancedMapView mapView,View control) 
	{ 
		if ((control instanceof DirectionPad)&&(control.getTag(R.id.
				control_listener)==null))
		{
			DirectionPad.DirectionPadListener directionListener=new DirectionPad.
					DirectionPadListener() 
			{
				@Override
				public void directionOperateStarted(DirectionPad directionPad,
						int directionX,int directionY) { }
				@Override
				public void directionOperateEnded(DirectionPad directionPad) { }
				
				@Override
				public void directionOperated(DirectionPad directionPad,
						int directionX,int directionY) 
				{
					if (panAmount==0)
					{
						panAmount=(int)Math.round(PAN_AMOUNT*MapControlDefsUtils.
								densityFactor);
						initializePanningAnimation(mapView.getController());
					}
					else
					{
						/*Because the animation duration is set to be exactly as 
						 *the amount of time to elapse before another contact of 
						 *the user with the control is registered, the animation 
						 *is supposed to have just finished at the point this 
						 *code executes. However, since timed events might not 
						 *be accurate, I have to handle the case that the 
						 *animation is still running and end it abruptly to let 
						 *the new animation start, but it probably won't affect 
						 *the smoothness since the animation is already 
						 *(supposed to be) close to completion.*/
						if (panningAnimator.isRunning()) panningAnimator.end();
						PropertyValuesHolder[] panningValuesHolders=
								panningAnimator.getValues();
						panningValuesHolders[0].setFloatValues(0,directionX*
								panAmount);
						panningValuesHolders[1].setFloatValues(0,directionY*
								panAmount);
					}
					lastAnimatedXValue=0; lastAnimatedYValue=0;
					//panningAnimator.start();
					mapView.getController().scrollBy(directionX*panAmount,
							directionY*panAmount);
				} //end directionOperated
			}; //end anonymous class
			((DirectionPad)control).addListener(directionListener);
			control.setTag(R.id.control_listener,directionListener);
		} //end if
	}
	
	private void initializePanningAnimation(final MapController controller)
	{
		PropertyValuesHolder panningXValuesHolder=PropertyValuesHolder.ofFloat(
				"panningX",0,panAmount);
		PropertyValuesHolder panningYValuesHolder=PropertyValuesHolder.ofFloat(
				"panningY",0,panAmount);
		panningAnimator=ValueAnimator.ofPropertyValuesHolder(panningXValuesHolder,
				panningYValuesHolder);
		panningAnimator.setInterpolator(new LinearInterpolator());
		panningAnimator.setDuration(PANNING_ANIMATION_DURATION);
		panningAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() 
		{	
			@Override public void onAnimationUpdate(ValueAnimator animator) 
			{
				float panningX=((Float)animator.getAnimatedValue("panningX")).
						floatValue()-lastAnimatedXValue;
				float panningY=((Float)animator.getAnimatedValue("panningY")).
						floatValue()-lastAnimatedYValue;
				controller.scrollBy(Math.round(panningX),Math.round(panningY));
				lastAnimatedXValue=panningX; lastAnimatedYValue=panningY;
			}
		});
	}
	
	@Override public int getMinimumWidth(Map<String,Object> properties)
	{
		if (controlWidth==0)
			controlWidth=Math.round(CONTROL_WIDTH*MapControlDefsUtils.
					densityFactor);
		return controlWidth;
	}
	
	@Override public int getMinimumHeight(Map<String,Object> properties)
	{
		if (controlHeight==0)
			controlHeight=Math.round(CONTROL_HEIGHT*MapControlDefsUtils.
					densityFactor);
		return controlHeight;
	}
	
	@Override public EnhancedMapView.ControlAlignment getDefaultAlignment()
	{ return EnhancedMapView.ControlAlignment.TOP_LEFT; }
}
