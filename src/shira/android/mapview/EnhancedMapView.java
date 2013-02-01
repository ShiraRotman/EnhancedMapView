package shira.android.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;

import com.google.android.maps.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnhancedMapView extends MapView
{
	//Can't be final since it's not assigned in the constructors
	private static float densityFactor; 
	private static final char[] operationChars={'4','6','8','2','7','1'};
	private static final Pattern operationStrPattern;
	//private static final Matrix identityMatrix=new Matrix();
	static final Map<ControlType,MapControlBuilder> controlBuilders; 
	
	private static final long CHANGE_TIMEOUT=2000;
	private static final int NUM_DIP_ZOOM_LEVEL=5;
	private static final int NUM_DIP_SCROLL=5;
	private static final int CONTROL_SPACING_DIP=5;
	
	/*Due to the fact that the touch gesture detectors don't have a common base 
	 *class/interface, I can't work with them as a group. This leads to 
	 *duplicate blocks of code and complex handling of touch motion events.
	 *In a future version it may be refactored.*/
	private GestureDetector touchScrollDetector;
	private GestureDetector touchLongPressDetector;
	private ScaleGestureDetector touchScaleDetector;
	/*The trackball gesture detector in Google Maps API actually contains no 
	 *functionality: All its functions just throw a RuntimeException when called.
	 *Therefore, for now I can't really use it, but I'm building the basis for a
	 *future version that will have it implemented. I may also choose to switch 
	 *to MapQuest at a certain point in the development, which has an API based 
	 *on that of Google and very similar to it, with a full implementation of 
	 *this class, or build my own implementation.*/
	private TrackballGestureDetector trackballDetector;
	private MotionEvent downMotionEvent,scrollMotionEvent;
	private MapViewGestureListener gestureListener;
	private List<MapViewChangeListener> changeListeners;
	private AnimationFinishCallback animCallback=new AnimationFinishCallback();
	private MapViewChangeTimeoutCallback changeTimeoutCallback;
	private GestureKind ongoingGestureKind=GestureKind.UNDETERMINED;
	private AnimationStage mapAnimationStage=AnimationStage.NOT_ANIMATING;
	private MapRotationController rotationController;
	private Matrix rotationMatrix=new Matrix();
	/*Temporary matrix for calculations, stored globally to save creating and 
	 *destroying a new object each time*/
	//private Matrix calculationMatrix=new Matrix();
	private GeoPoint previousMapCenter;
	private SparseArray<ControlViewData> mapControls;
	private float lastScrollX,lastScrollY;
	private int pointerID=-1,numPixelsZoomLevel,numPixelsScroll;
	private int pressedKeyCode=-1,keyRepeatCount,previousZoomLevel;
	private int controlSpacingPixels;
	private boolean useDefaultInputHandling=true,isHandlingScale=true;
	
	private enum GestureKind { UNDETERMINED,SCROLL,LONG_PRESS,SCALE }; 
	private enum AnimationStage { NOT_ANIMATING,ANIMATING,INTERRUPTED };
	private enum LayeringDirection { FORWARD,BACKWARD,CENTRALIZED };
	public enum ControlType { PAN,ZOOM,ROTATE,MAP_TYPE,STREET_VIEW };
	
	public enum ControlAlignment 
	{ 
		/*REMOVE,*/UNKNOWN,TOP_LEFT,TOP_CENTER,TOP_RIGHT,LEFT_TOP,LEFT_CENTER,
		LEFT_BOTTOM,BOTTOM_LEFT,BOTTOM_CENTER,BOTTOM_RIGHT,RIGHT_TOP,
		RIGHT_CENTER,RIGHT_BOTTOM;
	}
	
	private static class ControlViewData
	{
		public View control;
		public ControlAlignment alignment;
	}
	
	private class TouchGestureListener extends GestureDetector.
			SimpleOnGestureListener implements ScaleGestureDetector.
			OnScaleGestureListener
	{
		@Override public boolean onSingleTapUp(MotionEvent motionEvent)
		{ 
			rotateMotionEvent(motionEvent);
			handleSingleTap(motionEvent,getTouchGeoPoint(motionEvent));
			return true;
		}
		
		@Override public boolean onDoubleTap(MotionEvent motionEvent)
		{
			rotateMotionEvent(motionEvent);
			handleDoubleTap(motionEvent,getTouchGeoPoint(motionEvent));
			return true;
		}
		
		@Override public void onLongPress(MotionEvent motionEvent)
		{
			ongoingGestureKind=GestureKind.LONG_PRESS;
			MotionEvent cancelMotionEvent=createCancelGestureEvent(
					motionEvent);
			touchScrollDetector.onTouchEvent(cancelMotionEvent);
			if ((useDefaultInputHandling)&&(isHandlingScale))
				touchScaleDetector.onTouchEvent(cancelMotionEvent);
			rotateMotionEvent(motionEvent);
			handleLongPress(motionEvent,getTouchGeoPoint(motionEvent));
		}
		
		@Override public boolean onScroll(MotionEvent motionEvent1,MotionEvent 
				motionEvent2,float distanceX,float distanceY)
		{
			handleScroll(motionEvent1,motionEvent2,distanceX,distanceY);
			return true;
		}

		@Override public boolean onScale(ScaleGestureDetector detector) 
		{
			int numZoomLevels=Math.round(detector.getCurrentSpan()-detector.
					getPreviousSpan())/numPixelsZoomLevel;
			if (numZoomLevels==0) return false;
			else
			{
				MapController controller=getController();
				if (numZoomLevels>0)
				{
					for (int counter=1;counter<=numZoomLevels;counter++)
						controller.zoomOut();
				}
				else
				{
					numZoomLevels=-numZoomLevels;
					for (int counter=1;counter<=numZoomLevels;counter++)
						controller.zoomIn();
				}
				return true;
			}
		}

		@Override public boolean onScaleBegin(ScaleGestureDetector detector) 
		{ return true; }

		/*May need to handle the rest of the gesture that was not handled by the 
		 *onScale events? (Use onScale and copy it to a separate function)*/
		@Override public void onScaleEnd(ScaleGestureDetector detector) { }
	}
	
	private class TrackballLongPressCallback implements Runnable
	{
		@Override public void run()
		{
			/*Currently not supported because the trackball detector doesn't 
			 *supply the coordinates for this type of gesture. A future version 
			 *may calculate them using the explanation in onTrackballEvent.*/
		}
	}
	
	private class AnimationFinishCallback implements Runnable
	{
		@Override public void run() 
		{ mapAnimationStage=AnimationStage.NOT_ANIMATING; }
	}
	
	private class MapViewChangeTimeoutCallback implements Runnable
	{
		@Override public void run()
		{
			GeoPoint currentMapCenter=getMapCenter();
			if (!previousMapCenter.equals(currentMapCenter))
			{
				for (MapViewChangeListener changeListener:changeListeners)
				{
					changeListener.onPan(EnhancedMapView.this,previousMapCenter,
							currentMapCenter);
				}
				previousMapCenter=currentMapCenter;
			}
			int currentZoomLevel=getZoomLevel();
			if (previousZoomLevel!=currentZoomLevel)
			{
				for (MapViewChangeListener changeListener:changeListeners)
				{
					changeListener.onZoom(EnhancedMapView.this,previousZoomLevel,
							currentZoomLevel);
				}
				previousZoomLevel=currentZoomLevel;
			}
		} //end run
	} //end MapViewChangeTimeoutCallback
	
	static
	{
		StringBuilder patternBuilder=new StringBuilder();
		patternBuilder.append('[');
		for (int index=0;index<operationChars.length;index++)
			patternBuilder.append(operationChars[index]);
		patternBuilder.append(']');
		operationStrPattern=Pattern.compile(patternBuilder.toString());
		
		controlBuilders=new HashMap<ControlType,MapControlBuilder>();
		controlBuilders.put(ControlType.MAP_TYPE,new MapTypeControlBuilder());
		controlBuilders.put(ControlType.ZOOM,new ZoomControlBuilder());
		controlBuilders.put(ControlType.PAN,new PanControlBuilder());
		controlBuilders.put(ControlType.ROTATE,new RotateControlBuilder());
		controlBuilders.put(ControlType.STREET_VIEW,new StreetViewControlBuilder());
	}
	
	public EnhancedMapView(Context context,String apiKey)
	{ 
		super(context,apiKey);
		initialize();
	}
	
	public EnhancedMapView(Context context,AttributeSet attrs)
	{ this(context,attrs,0); }
	
	public EnhancedMapView(Context context,AttributeSet attrs,int defStyle)
	{ 
		super(context,attrs,defStyle);
		initialize();
	}
	
	private void initialize() 
	{ 
		setClickable(true); setLongClickable(true); setFocusable(true);
		setFocusableInTouchMode(true); setBuiltInZoomControls(false);
		setSatellite(true,false); getController().setZoom(14);
		previousMapCenter=getMapCenter(); previousZoomLevel=getZoomLevel();
		changeTimeoutCallback=new MapViewChangeTimeoutCallback();
		changeListeners=new LinkedList<MapViewChangeListener>();
		
		Context context=getContext();
		densityFactor=context.getResources().getDisplayMetrics().density;
		//Log.i("MapView","Density: " + densityFactor);
		MapControlDefsUtils.densityFactor=densityFactor;
		numPixelsZoomLevel=Math.round(NUM_DIP_ZOOM_LEVEL*densityFactor);
		numPixelsScroll=Math.round(NUM_DIP_SCROLL*densityFactor);
		controlSpacingPixels=Math.round(CONTROL_SPACING_DIP*densityFactor);
		mapControls=new SparseArray<ControlViewData>();
		
		TouchGestureListener touchGestureListener=new TouchGestureListener();
		touchScrollDetector=new GestureDetector(context,touchGestureListener);
		touchScrollDetector.setIsLongpressEnabled(false);
		touchScrollDetector.setOnDoubleTapListener(touchGestureListener);
		touchLongPressDetector=new GestureDetector(context,touchGestureListener);
		touchLongPressDetector.setIsLongpressEnabled(true);
		touchScaleDetector=new ScaleGestureDetector(context,touchGestureListener);
		
		//TrackballGestureDetector does not have a public constructor at this time
		//trackballDetector=new TrackballGestureDetector();
		//Throws RuntimeException
		//trackballDetector.registerLongPressCallback(new TrackballLongPressCallback());
	}
	
	public boolean isDefaultInputHandling() { return useDefaultInputHandling; }
	public void useDefaultInputHandling(boolean useDefaultInputHandling)
	{ this.useDefaultInputHandling=useDefaultInputHandling; }
	
	public MapViewGestureListener getMapViewGestureListener()
	{ return gestureListener; }
	public void setMapViewGestureListener(MapViewGestureListener listener)
	{ gestureListener=listener; }
	
	public void addChangeListener(MapViewChangeListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to register must be " + 
					"non-null!");
		changeListeners.add(listener);
	}
	
	public void removeChangeListener(MapViewChangeListener listener)
	{
		if (listener==null)
			throw new NullPointerException("The listener to unregister must " + 
					"be non-null!");
		changeListeners.remove(listener);
	}
	
	List<MapViewChangeListener> getChangeListeners() { return changeListeners; }
	
	/*@Override protected void measureChildren(int widthMeasureSpec,int 
			heightMeasureSpec)
	{
		super.measureChildren(widthMeasureSpec,heightMeasureSpec);
		Log.i("MapView","Dimensions: " + widthMeasureSpec + "," + 
				heightMeasureSpec);
	}*/
	
	@Override public void setSatellite(boolean on)
	{ setSatellite(on,true); }
	
	void setSatellite(boolean on,boolean notifyListeners)
	{
		super.setSatellite(on);
		if (notifyListeners)
		{
			for (MapViewChangeListener changeListener:changeListeners)
				changeListener.onTileChange(this,on);
		}
	}
	
	public boolean supportsRotation() { return (rotationController!=null); }
	
	public MapRotationController getRotationController()
	{
		if (rotationController!=null) return rotationController;
		else
		{
			throw new IllegalStateException("This map view does not support " +
					"rotation!");
		}
	}
	
	@Override protected void onAttachedToWindow()
	{
		try { rotationController=new MapRotationController(this); }
		catch (IllegalArgumentException argException) { }
	}
	
	@Override protected void onDetachedFromWindow()
	{ rotationController=null; }
	
	@Override 
	protected void onLayout(boolean changed,int left,int top,int right,int bottom)
	{
		if (right==0) return; //Sometimes the dimensions passed might be 0
		/*{
			left=((Number)getTag(R.id.view_left_coord)).intValue();
			top=((Number)getTag(R.id.view_top_coord)).intValue();
			//right+=left; bottom+=top;
		}*/
		Log.i("MapView","Stretched: " + left + "," + top);
		Log.i("MapView","End: " + right + "," + bottom);
		View parent=(View)getParent();
		int parentWidth=parent.getWidth(),parentHeight=parent.getHeight();
		int size=mapControls.size();
		for (int index=0;index<size;index++)
		{
			ControlViewData controlViewData=mapControls.valueAt(index);
			String alignmentName=controlViewData.alignment.name();
			int alignmentSeparator=alignmentName.indexOf("_");
			String alignmentNameX=alignmentName.substring(0,alignmentSeparator);
			String alignmentNameY=alignmentName.substring(alignmentSeparator+1);
			if ((alignmentNameX.equals("TOP"))||(alignmentNameY.equals("BOTTOM")))
			{
				String alignmentTemp=alignmentNameX;
				alignmentNameX=alignmentNameY;
				alignmentNameY=alignmentTemp;
			}
			int offsetX=calcOffsetX(alignmentNameX,left,right,parentWidth);
			int offsetY=calcOffsetY(alignmentNameY,top,bottom,parentHeight);
			Log.i("MapView","Offsets: " + offsetX + "," + offsetY);
			LayoutParams layoutParams=(LayoutParams)controlViewData.control.
					getLayoutParams();
			int childLeft=layoutParams.x+offsetX,childTop=layoutParams.y+offsetY;
			Log.i("MapView","Start: " + childLeft + "," + childTop);
			controlViewData.control.layout(childLeft,childTop,childLeft+
					layoutParams.width,childTop+layoutParams.height);
		}
	}
	
	private int calcOffsetX(String alignmentName,int left,int right,int 
			parentWidth)
	{
		if (alignmentName.equals("LEFT")) return -left;
		else if (alignmentName.equals("RIGHT")) return parentWidth-right;
		else return 0;
	}
	
	private int calcOffsetY(String alignmentName,int top,int bottom,int 
			parentHeight)
	{
		if (alignmentName.equals("TOP")) return -top;
		else if (alignmentName.equals("BOTTOM")) return parentHeight-bottom;
		else return 0;
	}
	
	@Override protected void dispatchDraw(Canvas canvas)
	{
		Handler handler=new Handler(Looper.getMainLooper());
		handler.removeCallbacks(changeTimeoutCallback);
		if (rotationController!=null)
		{
			float rotationDegrees=rotationController.getMapRotation();
			//Log.i("MapView","Degrees: " + rotationDegrees);
			canvas.getMatrix(rotationMatrix); //Store for transformation use
			if (rotationDegrees>0)
			{
				int saveCount=canvas.save(Canvas.MATRIX_SAVE_FLAG);
				canvas.rotate(-rotationDegrees,getWidth()/2,getHeight()/2);
				super.dispatchDraw(canvas);
				canvas.restoreToCount(saveCount);
			}
			else super.dispatchDraw(canvas);
		}
		else super.dispatchDraw(canvas);
		handler.postDelayed(changeTimeoutCallback,CHANGE_TIMEOUT);
	}
	
	@Override public boolean onKeyDown(int keyCode,KeyEvent keyEvent)
	{
		Log.i("MapView","Key: " + keyCode);
		boolean consumed=false;
		Iterator<Overlay> overlaysIterator=getOverlays().iterator();
		while ((!consumed)&&(overlaysIterator.hasNext()))
			consumed=overlaysIterator.next().onKeyDown(keyCode,keyEvent,this);
		if ((!consumed)&&(useDefaultInputHandling))
		{
			int action=keyEvent.getAction();
			if (action==KeyEvent.ACTION_DOWN)
			{
				char pressedKeyChar=getPressedKeyChar(keyEvent);
				Log.i("MapView","Char: " + pressedKeyChar);
				consumed=((keyCode==pressedKeyCode)||(isOperationChar(
						pressedKeyChar)));
				if ((consumed)&&(!keyEvent.isLongPress()))
				{
					pressedKeyCode=keyCode;
					keyRepeatCount=keyEvent.getRepeatCount();
				}
				else { pressedKeyCode=-1; keyRepeatCount=0; }
			} //end if (ACTION_DOWN)
			else if (action==KeyEvent.ACTION_MULTIPLE)
			{
				if (keyCode!=KeyEvent.KEYCODE_UNKNOWN)
				{
					char pressedKeyChar=getPressedKeyChar(keyEvent);
					consumed=handleInputChar(pressedKeyChar,keyEvent.
							getRepeatCount());
				}
				else
				{
					String characters=keyEvent.getCharacters();
					Matcher matcher=operationStrPattern.matcher(characters);
					if (matcher.matches()) 
					{
						int length=characters.length();
						for (int index=0;index<length;index++)
							handleInputChar(characters.charAt(index),1);
						consumed=true;
					}
				}
				pressedKeyCode=-1; keyRepeatCount=0;
			} //end if (ACTION_MULTIPLE)
		} //end if (useDefaultInputHandling)
		return consumed;
	}
	
	@Override public boolean onKeyUp(int keyCode,KeyEvent keyEvent)
	{
		boolean consumed=false;
		Iterator<Overlay> overlaysIterator=getOverlays().iterator();
		while ((!consumed)&&(overlaysIterator.hasNext()))
			consumed=overlaysIterator.next().onKeyUp(keyCode,keyEvent,this);
		Log.i("MapView","Up: " + keyCode);
		if ((!consumed)&&(keyCode==pressedKeyCode)&&(!keyEvent.isCanceled()))
			handleInputChar(getPressedKeyChar(keyEvent),keyRepeatCount+1);
		pressedKeyCode=-1; keyRepeatCount=0;
		return consumed;		
	}
	
	private boolean isOperationChar(char character)
	{
		boolean found=false; int index=0;
		while ((!found)&&(index<operationChars.length))
			if (character==operationChars[index]) found=true; else index++;
		return found;
	}
	
	private char getPressedKeyChar(KeyEvent keyEvent)
	{
		int keyCode=keyEvent.getKeyCode();
		InputDevice inputDevice=keyEvent.getDevice();
		KeyCharacterMap keyCharMap=inputDevice.getKeyCharacterMap();
		return keyCharMap.getNumber(keyCode);
	}
	
	private boolean handleInputChar(char keyChar,int keyRepeatCount)
	{
		Log.i("MapView","Handling: " + keyChar);
		MapController controller=getController();
		switch (keyChar)
		{
			case '4': 
				controller.scrollBy(-numPixelsScroll*keyRepeatCount,0);
				break;
			case '6':
				Log.i("MapView","Scrolling: " + keyRepeatCount);
				controller.scrollBy(numPixelsScroll*keyRepeatCount,0);
				break;
			case '2':
				controller.scrollBy(0,-numPixelsScroll*keyRepeatCount);
				break;
			case '8':
				controller.scrollBy(0,numPixelsScroll*keyRepeatCount);
				break;
			case '1':
				controller.setZoom(getZoomLevel()+keyRepeatCount);
				break;
			case '7':
				controller.setZoom(getZoomLevel()-keyRepeatCount);
				break;
			default: return false;
		}
		return true;
	}
	
	@Override public boolean onTouchEvent(MotionEvent motionEvent)
	{
		if ((!isEnabled())||(!isClickable())) return false;
		/*Since this function doesn't call the implementation of its baseclass 
		 *(super), I have to provide any functionality I want to retain by 
		 *myself, such as delegating the event to the overlays before handling 
		 *it in the map view.*/
		Iterator<Overlay> overlaysIterator=getOverlays().iterator();
		boolean consumed=false;
		while ((!consumed)&&(overlaysIterator.hasNext()))
			consumed=overlaysIterator.next().onTouchEvent(motionEvent,this);
		if (consumed) return true;
		//int actionMask=motionEvent.getAction() & MotionEvent.ACTION_MASK;
		int actionMask=motionEvent.getActionMasked();
		Log.i("MapView","Handling: " + actionMask);
		switch (actionMask)
		{
			case MotionEvent.ACTION_DOWN: 
				if (mapAnimationStage==AnimationStage.ANIMATING)
				{
					getController().stopAnimation(false);
					mapAnimationStage=AnimationStage.INTERRUPTED;
				}
				pointerID=motionEvent.getPointerId(0);
				Log.i("MapView","Pointer set: " + pointerID);
				touchScrollDetector.onTouchEvent(motionEvent);
				if (isLongClickable()) 
					touchLongPressDetector.onTouchEvent(motionEvent);
				/*else if (!useDefaultGestures)
				{
					ongoingGestureKind=GestureKind.SCROLL;
					touchScrollDetector.onTouchEvent(motionEvent);
				}*/
				if (useDefaultInputHandling) 
					isHandlingScale=touchScaleDetector.onTouchEvent(motionEvent);
				//if (ongoingGestureKind==GestureKind.UNDETERMINED)
				//downMotionEvent=motionEvent;
				break;
			case MotionEvent.ACTION_MOVE:
				if (ongoingGestureKind==GestureKind.UNDETERMINED)
				{
					ongoingGestureKind=GestureKind.SCROLL;
					/*touchScrollDetector.onTouchEvent(downMotionEvent);
					determined=true;*/
					MotionEvent cancelMotionEvent=createCancelGestureEvent(
							motionEvent);
					if (isLongClickable()) 
						touchLongPressDetector.onTouchEvent(cancelMotionEvent);
					if ((useDefaultInputHandling)&&(isHandlingScale))
						touchScaleDetector.onTouchEvent(cancelMotionEvent);
				}
				updateGestureDetector(motionEvent);
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				if ((ongoingGestureKind==GestureKind.UNDETERMINED)&&
						(useDefaultInputHandling))
				{
					ongoingGestureKind=GestureKind.SCALE;
					/*touchScaleDetector.onTouchEvent(downMotionEvent);
					determined=true;*/
					MotionEvent cancelMotionEvent=createCancelGestureEvent(
							motionEvent);
					touchScrollDetector.onTouchEvent(cancelMotionEvent);
					if (isLongClickable()) 
						touchLongPressDetector.onTouchEvent(cancelMotionEvent);
				}
				updateGestureDetector(motionEvent);
				break;
			default:
				if (ongoingGestureKind==GestureKind.UNDETERMINED)
				{
					ongoingGestureKind=GestureKind.SCROLL;
					MotionEvent cancelMotionEvent=createCancelGestureEvent(
							motionEvent);
					if (isLongClickable()) 
						touchLongPressDetector.onTouchEvent(cancelMotionEvent);
					if ((useDefaultInputHandling)&&(isHandlingScale))
						touchScaleDetector.onTouchEvent(cancelMotionEvent);
				}
				updateGestureDetector(motionEvent);
				if ((actionMask==MotionEvent.ACTION_UP)||(actionMask==
						MotionEvent.ACTION_CANCEL))
				{
					ongoingGestureKind=GestureKind.UNDETERMINED;
					//downMotionEvent=null; 
					pointerID=-1; isHandlingScale=true;
				}
		} //end switch
		return true;
	}
	
	private void updateGestureDetector(MotionEvent motionEvent)
	{
		Log.i("MapView","Detector: " + ongoingGestureKind);
		switch (ongoingGestureKind)
		{
			case SCROLL:
				touchScrollDetector.onTouchEvent(motionEvent);
				break;
			case LONG_PRESS:
				touchLongPressDetector.onTouchEvent(motionEvent);
				break;
			case SCALE:
				if (isHandlingScale)
					isHandlingScale=touchScaleDetector.onTouchEvent(motionEvent);
				break;
		}
	}
	
	private static MotionEvent createCancelGestureEvent(MotionEvent sourceEvent)
	{
		MotionEvent cancelMotionEvent=MotionEvent.obtain(sourceEvent);
		cancelMotionEvent.setAction(MotionEvent.ACTION_CANCEL);
		return cancelMotionEvent;
	}
	
	private void handleSingleTap(MotionEvent motionEvent,GeoPoint geoPoint)
	{
		Log.i("MapView","Single Tap");
		if (mapAnimationStage==AnimationStage.INTERRUPTED)
			mapAnimationStage=AnimationStage.NOT_ANIMATING;
		else if (geoPoint!=null)
		{
			boolean consumed=false;
			Iterator<Overlay> overlaysIterator=getOverlays().iterator();
			while ((!consumed)&&(overlaysIterator.hasNext()))
				consumed=overlaysIterator.next().onTap(geoPoint,this);
			if ((!consumed)&&(gestureListener!=null))
				gestureListener.onSingleTap(this,geoPoint,motionEvent);
		}
	}
	
	private void handleDoubleTap(MotionEvent motionEvent,GeoPoint geoPoint)
	{
		Log.i("MapView","Double Tap, " + mapAnimationStage);
		if (geoPoint==null) return;
		boolean consumed=false;
		switch (mapAnimationStage)
		{
			case NOT_ANIMATING:
				//TODO: Handle overlays with a double tap
				if ((!consumed)&&(useDefaultInputHandling))
				{
					Log.i("MapView","Animating");
					mapAnimationStage=AnimationStage.ANIMATING;
					getController().animateTo(geoPoint,animCallback);
					consumed=true;
				}
				break;
			case INTERRUPTED:
				mapAnimationStage=AnimationStage.NOT_ANIMATING;
				/*A double tap during animation is considered a single tap if a
				 *layer chooses to handle it*/ 
				Iterator<Overlay> overlaysIterator=getOverlays().iterator();
				while ((!consumed)&&(overlaysIterator.hasNext()))
					consumed=overlaysIterator.next().onTap(geoPoint,this);
				break;
		}
		if ((!consumed)&&(gestureListener!=null))
			gestureListener.onDoubleTap(this,geoPoint,motionEvent);
	}
	
	private void handleLongPress(MotionEvent motionEvent,GeoPoint geoPoint)
	{
		if (mapAnimationStage==AnimationStage.INTERRUPTED)
			mapAnimationStage=AnimationStage.NOT_ANIMATING;
		else if (geoPoint!=null)
		{
			boolean consumed=false;
			//TODO: Handle overlays
			if ((!consumed)&&(gestureListener!=null))
				gestureListener.onLongPress(this,geoPoint,motionEvent);
		}
	}
	
	private void rotateMotionEvent(MotionEvent motionEvent)
	{
		if (rotationController!=null)
		{
			//TODO: Test and fix when working on the overlays
			float rotationDegrees=rotationController.getMapRotation();
			if (rotationDegrees>0)
			//if (!rotationMatrix.isIdentity())
			{
				if (rotationMatrix==null) rotationMatrix=new Matrix();
				else rotationMatrix.reset();
				rotationMatrix.setRotate(rotationDegrees,getWidth()/2,
						getHeight()/2);
				//May contain translation components
				//motionEvent.transform(rotationMatrix);
			}
		}
	}
	
	private GeoPoint getTouchGeoPoint(MotionEvent motionEvent)
	{
		Log.i("MapView","Pointer: " + pointerID);
		int pointerIndex=motionEvent.findPointerIndex(pointerID);
		if (pointerIndex>-1)
		{
			int touchX=Math.round(motionEvent.getX(pointerIndex));
			int touchY=Math.round(motionEvent.getY(pointerIndex));
			return getProjection().fromPixels(touchX,touchY);
		}
		else return null;
	}
	
	private void handleScroll(MotionEvent motionEvent1,MotionEvent motionEvent2,
			float distanceX,float distanceY)
	{
		if (mapAnimationStage==AnimationStage.INTERRUPTED)
			mapAnimationStage=AnimationStage.NOT_ANIMATING;
		if (useDefaultInputHandling)
			getController().scrollBy(Math.round(distanceX),Math.round(distanceY));
		else if (gestureListener!=null)
		{
			GeoPoint centerPoint=getMapCenter();
			int centerX=centerPoint.getLatitudeE6();
			int centerY=centerPoint.getLongitudeE6();
			int halfWidth=getLatitudeSpan()/2,halfHeight=getLongitudeSpan()/2;
			GeoPoint topLeft=new GeoPoint(centerX-halfWidth,centerY-halfHeight);
			GeoPoint bottomRight=new GeoPoint(centerX+halfWidth,centerY+
					halfHeight);
			gestureListener.onScroll(this,topLeft,bottomRight,motionEvent1,
					motionEvent2,distanceX,distanceY);
		}
	}
	
	@Override public boolean onTrackballEvent(MotionEvent motionEvent)
	{
		Iterator<Overlay> overlaysIterator=getOverlays().iterator();
		boolean consumed=false;
		while ((!consumed)&&(overlaysIterator.hasNext()))
			consumed=overlaysIterator.next().onTrackballEvent(motionEvent,this);
		if (consumed) return true;
		else
		{
			trackballDetector.analyze(motionEvent);
			/*Since trackball devices have only one button, there are no motion 
			 *events associated with a pointer index, so we don't have to use 
			 *the masked value when interpreting them.*/
			int action=motionEvent.getAction();
			switch (action)
			{
				case MotionEvent.ACTION_DOWN: 
					if (downMotionEvent==null) downMotionEvent=motionEvent;
					else if (trackballDetector.isDoubleTap()) 
					{
						/*Each input source class has a different meaning to the  
						 *pointer coordinates in motion events (see the 
						 *documentation of the MotionEvent and InputDevice
						 *classes). In the case of the trackball class, the 
						 *coordinates represent the relative movement, i.e the 
						 *position difference from the previous event (which 
						 *means that for down and up events, the coordinates 
						 *are zeroed), and in device-specific units instead of 
						 *pixels. Thus, they have to undergo a translation to be 
						 *compatible with motion events from other input source 
						 *classes. This can be done by obtaining the motion 
						 *range for the X and Y axes, and using interpolation to 
						 *convert the coordinates to be in pixel units, taking 
						 *density into account (see MotionEvent.getDevice,
						 *InputDevice.getMotionRange). Fortunately, the 
						 *trackball detector already does the conversion for the 
						 *simple gestures.*/
						int pointX=Math.round(trackballDetector.getFirstDownX());
						int pointY=Math.round(trackballDetector.getFirstDownY());
						GeoPoint geoPoint=getProjection().fromPixels(pointX,pointY);
						handleDoubleTap(downMotionEvent,geoPoint);
						downMotionEvent=null;
					}
					break;
				case MotionEvent.ACTION_MOVE:
					/*Unlike scroll touch events, which always have a down event 
					 *and an up event that indicate the start and end of the 
					 *scroll (since the user must stay in contact with the 
					 *screen during the whole operation), with trackball devices 
					 *the user can directly perform a scroll, and the button on 
					 *the device, which is what generates the down and up 
					 *events, is actually unrelated to the scroll and not needed 
					 *for it. This makes it a little tricky to know when a 
					 *scroll sequence ends and another begins. Actually there's 
					 *no way to determine that a scroll finished because when it 
					 *happens, the scroll events just stop occurring, and I 
					 *can't use a timer which resets every time an event occurs 
					 *because it might catch the next sequence. Therefore, only 
					 *when there's a scroll event and the state of the device 
					 *indicates that it's not scrolling at that time, I can know 
					 *for sure that a new sequence began, and thus the previous 
					 *sequence has ended.*/
					if (!trackballDetector.isScroll())
					{
						scrollMotionEvent=motionEvent;
						lastScrollX=0; lastScrollY=0;
					}
					else
					{
						float distanceX=trackballDetector.scrollX()-lastScrollX;
						float distanceY=trackballDetector.scrollY()-lastScrollY;
						handleScroll(scrollMotionEvent,motionEvent,distanceX,
								distanceY);
						lastScrollX=trackballDetector.scrollX();
						lastScrollY=trackballDetector.scrollY();
					}
					break;
				case MotionEvent.ACTION_UP:
					if (trackballDetector.isTap())
					{
						int pointX=Math.round(trackballDetector.getCurrentDownX());
						int pointY=Math.round(trackballDetector.getCurrentDownY());
						GeoPoint geoPoint=getProjection().fromPixels(pointX,pointY);
						handleSingleTap(motionEvent,geoPoint);
						downMotionEvent=null;
					}
					break;
			} //end switch
			return true;
		} //end else
	}
	
	public void setMapControl(ControlType controlType,Map<String,Object> 
			properties)
	{ setMapControl(controlType,properties,ControlAlignment.UNKNOWN,View.VISIBLE); }
		
	public void setMapControl(ControlType controlType,Map<String,Object> 
			properties,ControlAlignment alignment,int visibility)
	{
		if ((getWidth()==0)||(getHeight()==0))
			throw new IllegalStateException("The map controls cannot be set " +
					"before the map view is ready!");
		if ((visibility!=View.VISIBLE)&&(visibility!=View.INVISIBLE)&&
				(visibility!=View.GONE))
			throw new IllegalArgumentException("The control's visibility must be " +
					"one of the allowed values for the visibility of a view!");
		ControlViewData controlViewData=mapControls.get(controlType.ordinal());
		if (controlViewData==null) controlViewData=new ControlViewData();
		/*else if (alignment==ControlAlignment.REMOVE)
			throw new IllegalArgumentException("The control does not exist on " +
					"the map and thus cannot be removed!");*/
		View existingControl=controlViewData.control;
		MapControlBuilder controlBuilder=controlBuilders.get(controlType);
		View createdControl=controlBuilder.buildControl(properties,
				existingControl,this);
		//int controlWidth=createdControl.getMeasuredWidth();
		int controlWidth=controlBuilder.getMinimumWidth(properties);
		int controlHeight=controlBuilder.getMinimumHeight(properties);
		if (alignment==ControlAlignment.UNKNOWN) 
			alignment=controlBuilder.getDefaultAlignment();
		
		/*Check if rearranging controls, i.e moving them to make space for the 
		 *new / modified control, and/or to cover the space of the control if it 
		 *has been moved, is needed*/
		LayoutParams layoutParams=null; int prevWidth=0,prevHeight=0; 
		boolean rearrange=false;
		if (existingControl!=null)
		{
			layoutParams=(LayoutParams)existingControl.getLayoutParams();
			prevWidth=layoutParams.width; prevHeight=layoutParams.height;
			if (alignment!=controlViewData.alignment) rearrange=true;
			else
			{
				if (existingControl!=createdControl)
				{
					if ((prevWidth!=controlWidth)||(prevHeight!=controlHeight))
						rearrange=true;
				}
				int prevVisibility=existingControl.getVisibility();
				if ((!rearrange)&&(visibility!=prevVisibility)&&((visibility==
						View.GONE)||(prevVisibility==View.GONE)))
					rearrange=true;
			}
		} //end if existingControl...
		
		/*Find the farthest control from the start position of its alignment, or 
		 *the control itself if it already exists and hasn't changed alignment*/ 
		/*LayoutParams layoutParams; int prevWidth=0,prevHeight=0;
		if (alignment==controlViewData.alignment)
		{
			layoutParams=(LayoutParams)existingControl.getLayoutParams();
			prevWidth=layoutParams.width; prevHeight=layoutParams.height;
		}
		else layoutParams=null;*/
		if ((existingControl==null)||(rearrange)) /*&&(alignment!=ControlAlignment.
				REMOVE))*/
		{
			boolean isHorizontalChange=isHorizontalAlignment(alignment);
			LayeringDirection direction=getAlignmentDirection(alignment);
			String alignmentStr=alignment.name(); int startPosition;
			if ((alignmentStr.endsWith("LEFT"))||(alignmentStr.endsWith("TOP")))
				startPosition=0;
			else if (alignmentStr.endsWith("RIGHT")) startPosition=getWidth();
			else if (alignmentStr.endsWith("BOTTOM")) startPosition=getHeight();
			else if (isHorizontalChange) startPosition=getWidth()/2;
			else startPosition=getHeight()/2;
			/*List<View> alignedControls;
			if (alignment==controlViewData.alignment)
				alignedControls=new LinkedList<View>();
			else alignedControls=null;*/
			int maxDistance=0,maxDistanceOtherSide=0;
			int size=mapControls.size(); boolean foundControl=false;
			for (int counter=0;counter<size;counter++)
			{
				ControlViewData traversedViewData=mapControls.valueAt(counter);
				View traversedControl=traversedViewData.control;
				ControlAlignment traversedAlignment=traversedViewData.
						alignment;
				LayoutParams traversedLayout=(LayoutParams)traversedControl.
						getLayoutParams();
				if (traversedAlignment==alignment)
				{
					if (!foundControl)
					{
						if (traversedControl!=existingControl)
						{
							/*Rect bounds=new Rect();	
							traversedControl.getDrawingRect(bounds);*/
							int right=traversedLayout.x+traversedLayout.width;
							int bottom=traversedLayout.y+traversedLayout.height;
							int positionDifference=0;
							int positionDifferenceOtherSide=0;
							switch (direction)
							{
								case FORWARD:
									positionDifference=(isHorizontalChange?right:
											bottom)-startPosition;
									break;
								case BACKWARD:
									positionDifference=(isHorizontalChange?
											traversedLayout.x:traversedLayout.y)-
											startPosition;
									break;
								case CENTRALIZED:
									int position1,position2;
									if (isHorizontalChange)
									{ position1=traversedLayout.x; position2=right; }
									else 
									{ position1=traversedLayout.y; position2=bottom; }
									int distance1=position1-startPosition;
									int distance2=position2-startPosition;
									Log.i("MapView","Distances: " + distance1 + 
											" " + distance2);
									if (Math.abs(distance1)>Math.abs(distance2))
									{
										positionDifference=distance1;
										if (Math.signum(distance1)!=Math.signum(distance2))
											positionDifferenceOtherSide=distance2;
									}
									else
									{
										positionDifference=distance2;
										if (Math.signum(distance1)!=Math.signum(distance2))
											positionDifferenceOtherSide=distance1;
									}
									break;
							} //end switch
							Log.i("MapView","Difference: " + positionDifference);
							if ((Math.signum(positionDifference)!=Math.signum(
									maxDistance))&&(maxDistance!=0))
							{
								int tempDifference=positionDifference;
								positionDifference=positionDifferenceOtherSide;
								positionDifferenceOtherSide=tempDifference;
							}
							if (Math.abs(positionDifference)>Math.abs(maxDistance))
								maxDistance=positionDifference;
							if (Math.abs(positionDifferenceOtherSide)>Math.abs(
									maxDistanceOtherSide))
								maxDistanceOtherSide=positionDifferenceOtherSide;
						} //end if traversedControl!=existingControl 
						else foundControl=true;
					} //end if !foundControl
					/*if (alignedControls!=null) 
						alignedControls.add(traversedControl);*/
				} //end if traversedAlignment==alignment
				else if (traversedControl!=existingControl)
				{
					String traversedAlignmentStr=traversedAlignment.name();
					if ((!isHorizontalChange)&&((traversedAlignmentStr.
							startsWith("TOP"))||(traversedAlignmentStr.
							startsWith("BOTTOM")))&&((maxDistance+startPosition)<
							traversedLayout.height))
					{
						maxDistance=traversedLayout.height-startPosition;
						/*if (direction==LayeringDirection.BACKWARD)
							maxDistance*=-1;*/
					}
				}
			} //end for
			Log.i("MapView","Final distances: " + maxDistance + " " + 
					maxDistanceOtherSide);
			if ((maxDistanceOtherSide!=0)&&(Math.abs(maxDistanceOtherSide)<
					Math.abs(maxDistance)))
				maxDistance=maxDistanceOtherSide;
			
			//Calculate the new rectangle for the control and update the layout
			if (!foundControl)
			{
				Log.i("MapView","Position: " + startPosition);
				int positionCoord=maxDistance+startPosition;
				int spacing=controlSpacingPixels;
				switch (direction)
				{
					case BACKWARD: spacing*=-1; break;
					case CENTRALIZED: spacing*=Math.signum(maxDistance); break;
				}
				positionCoord+=spacing;
				int positionX,positionY;
				if (isHorizontalChange) 
				{
					positionY=(alignmentStr.startsWith("TOP")?controlSpacingPixels:
							getHeight()-controlSpacingPixels-controlHeight);
					positionX=positionCoord;
					if ((direction==LayeringDirection.BACKWARD)||(maxDistance<0)) 
						positionX-=controlWidth;
					else if ((direction==LayeringDirection.CENTRALIZED)&&
							(maxDistance==0))
						positionX-=controlWidth/2;
				}
				else 
				{
					positionX=(alignmentStr.startsWith("LEFT")?controlSpacingPixels:
							getWidth()-controlSpacingPixels-controlWidth);
					positionY=positionCoord;
					if ((direction==LayeringDirection.BACKWARD)||(maxDistance<0)) 
						positionY-=controlHeight;
					else if ((direction==LayeringDirection.CENTRALIZED)&&
							(maxDistance==0))
						positionY-=controlHeight/2;
				}
				/*int alignmentParam;
				if ((direction==LayeringDirection.CENTRALIZED)&&(maxDistance==0))
				{
					alignmentParam=(isHorizontalChange?LayoutParams.
							CENTER_HORIZONTAL:LayoutParams.CENTER_VERTICAL);
				}
				else alignmentParam=LayoutParams.TOP_LEFT;*/
				layoutParams=new LayoutParams(controlWidth,controlHeight,
						positionX,positionY,LayoutParams.TOP_LEFT);
			} //end if !foundControl
			else if ((layoutParams.width!=controlWidth)||(layoutParams.height!=
					controlHeight))
			{
				layoutParams.width=controlWidth;
				layoutParams.height=controlHeight;
			}
			
			//Rearrange existing controls to occupy the change in the layout
			if (rearrange)
			{
				boolean isPrevHorizontalChange=isHorizontalAlignment(
						controlViewData.alignment);
				int moveAmount=(isPrevHorizontalChange?prevWidth:prevHeight); 
				boolean expanded=false;
				if (alignment==controlViewData.alignment)
				{
					if (visibility!=existingControl.getVisibility())
					{
						if (visibility==View.GONE) expanded=false;
						else if (existingControl.getVisibility()==View.GONE)
						{
							moveAmount=(isPrevHorizontalChange?controlWidth:
									controlHeight);
							expanded=true;
						}
					}
					else
					{
						moveAmount=(isPrevHorizontalChange?controlWidth-
								prevWidth:controlHeight-prevHeight);
						if (moveAmount>0) expanded=true; 
						else
						{
							moveAmount*=-1;
							expanded=false;
						}
					}
				} //end if alignment...
				else expanded=false;
				LayeringDirection prevDirection=getAlignmentDirection(
						controlViewData.alignment);
				for (int counter=0;counter<size;counter++)
				{
					ControlViewData traversedViewData=mapControls.get(counter);
					if ((traversedViewData.control!=existingControl)&&
							(traversedViewData.alignment==controlViewData.
							alignment))
					{
						moveLayoutControl(existingControl,traversedViewData.
								control,isPrevHorizontalChange,prevDirection,
								expanded,moveAmount);
					}
				}
			} //end if rearrange
		} //end if existingControl==null || rearrange
		
		//Update the control in its parent view
		Log.i("MapView","Layout: " + layoutParams.x + "," + layoutParams.y + 
				" " + layoutParams.width + "," + layoutParams.height);
		Log.i("MapView","Alignment: " + alignment.name());
		//if (alignment==ControlAlignment.REMOVE) removeView(existingControl);
		if ((existingControl==null)||(prevWidth!=layoutParams.width)||
				(prevHeight!=layoutParams.height))
		{
			createdControl.setLayoutParams(layoutParams);
			controlBuilder.registerListeners(this,createdControl);
		}
		createdControl.setVisibility(visibility);
		if (existingControl!=createdControl)
		{
			ViewsActivationManager activationManager=ViewsActivationManager.
					getInstance();
			if (existingControl!=null)
			{
				activationManager.unregisterView(existingControl);				
				removeView(existingControl);
				MapViewChangeListener listener=(MapViewChangeListener)
						existingControl.getTag(R.id.change_listener);
				if (listener!=null)
				{
					removeChangeListener(listener);
					existingControl.setTag(R.id.change_listener,null);
				}
				//The other cases requiring registration were addressed above
				controlBuilder.registerListeners(this,createdControl);
			}
			//activationManager.registerView(createdControl);
			addView(createdControl,layoutParams);
			//createdControl.requestLayout();
			controlViewData.control=createdControl;
			//controlBuilder.registerListeners(this,createdControl);
		}
		controlViewData.alignment=alignment;
		if (mapControls.get(controlType.ordinal())==null)
			mapControls.put(controlType.ordinal(),controlViewData);
	} //end setMapControl
	
	private boolean isHorizontalAlignment(ControlAlignment alignment)
	{ 
		String alignmentStr=alignment.name();
		return ((alignmentStr.startsWith("TOP"))||(alignmentStr.startsWith(
				"BOTTOM")));
	}
	
	private LayeringDirection getAlignmentDirection(ControlAlignment alignment)
	{
		String alignmentStr=alignment.name();
		if (alignmentStr.endsWith("CENTER")) 
			return LayeringDirection.CENTRALIZED;
		else if ((alignmentStr.endsWith("LEFT"))||(alignmentStr.endsWith("TOP")))
			return LayeringDirection.FORWARD;
		else return LayeringDirection.BACKWARD;
	}
	
	private void moveLayoutControl(View affectingControl,View affectedControl,
			boolean isHorizontalChange,LayeringDirection direction,boolean 
			expanded,int moveAmount)
	{
		int moveSign=0;
		if (direction==LayeringDirection.FORWARD) moveSign=1;
		else if (direction==LayeringDirection.BACKWARD) moveSign=-1;
		LayoutParams affectingLayoutParams=(LayoutParams)affectingControl.
				getLayoutParams();
		LayoutParams affectedLayoutParams=(LayoutParams)affectedControl.
				getLayoutParams();
		int dimension; boolean canMove=false,testMove=false;
		int affectingControlStart,affectingControlEnd,affectedControlStart;
		if (isHorizontalChange)
		{
			dimension=getWidth();
			//moveAmount=(amount==0?affectingControl.getMeasuredWidth():amount);
			affectingControlStart=affectingLayoutParams.x;
			Log.i("MapView","Start: " + affectingLayoutParams.x);
			affectingControlEnd=affectingLayoutParams.x+affectingLayoutParams.width;
			affectedControlStart=affectedLayoutParams.x;
		}
		else
		{
			dimension=getHeight();
			//moveAmount=(amount==0?affectingControl.getMeasuredHeight():amount);
			affectingControlStart=affectingLayoutParams.y;
			affectingControlEnd=affectingLayoutParams.y+affectingLayoutParams.height;
			affectedControlStart=affectedLayoutParams.y;
		}
		if (direction==LayeringDirection.CENTRALIZED)
		{
			int middle=dimension/2;
			int affectingStartDistance=affectingControlStart-middle;
			int affectingEndDistance=affectingControlEnd-middle;
			int signum=(int)Math.signum(affectingStartDistance);
			Log.i("MapView","Affecting: " + affectingStartDistance + " " + 
					affectingEndDistance);
			if (signum==Math.signum(affectingEndDistance))
			{
				//Not central control
				if (signum==Math.signum(affectedControlStart-middle))
				{
					testMove=true;
					moveSign=signum;
				}
			}
			else
			{
				canMove=true;
				moveAmount/=2;
				moveSign=(int)(Math.signum(affectedControlStart-middle));
			}
		} //end if direction...
		else testMove=true;
		moveAmount+=controlSpacingPixels;
		Log.i("MapView","Checking: " + affectingControlStart + " and " + 
				affectedControlStart + " with sign " + moveSign);
		if ((canMove)||((testMove)&&(affectingControlStart*moveSign<
				affectedControlStart*moveSign)))
		{
			if (!expanded) moveSign*=-1;
			moveAmount*=moveSign;
			Log.i("MapView","Moving by " + moveAmount);
			if (isHorizontalChange) affectedLayoutParams.x+=moveAmount;
			else affectedLayoutParams.y+=moveAmount;
			affectedControl.setLayoutParams(affectedLayoutParams);
		}
	} //end moveLayoutControl
}
