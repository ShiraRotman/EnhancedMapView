package shira.android.mapview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;

import com.google.android.maps.*;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnhancedMapView extends MapView
{
	private static final char[] operationChars={'4','6','8','2','7','1'};
	private static final Pattern operationStrPattern;
	
	private static final int NUM_DIP_ZOOM_LEVEL=5;
	private static final int NUM_DIP_SCROLL=5;
	
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
	private AnimationFinishCallback animCallback=new AnimationFinishCallback();
	private GestureKind ongoingGestureKind=GestureKind.UNDETERMINED;
	private AnimationStage mapAnimationStage=AnimationStage.NOT_ANIMATING;
	private float lastScrollX,lastScrollY;
	private int pointerID=-1,numPixelsZoomLevel,numPixelsScroll;
	private int pressedKeyCode=-1,keyRepeatCount;
	private boolean useDefaultInputHandling=true,isHandlingScale=true;
	
	private enum GestureKind { UNDETERMINED,SCROLL,LONG_PRESS,SCALE }; 
	private enum AnimationStage { NOT_ANIMATING,ANIMATING,INTERRUPTED };
	
	private class TouchGestureListener extends GestureDetector.
			SimpleOnGestureListener implements ScaleGestureDetector.
			OnScaleGestureListener
	{
		@Override public boolean onSingleTapUp(MotionEvent motionEvent)
		{ 
			handleSingleTap(motionEvent,getTouchGeoPoint(motionEvent));
			return true;
		}
		
		@Override public boolean onDoubleTap(MotionEvent motionEvent)
		{
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
	
	static
	{
		StringBuilder patternBuilder=new StringBuilder();
		patternBuilder.append('[');
		for (int index=0;index<operationChars.length;index++)
			patternBuilder.append(operationChars[index]);
		patternBuilder.append(']');
		operationStrPattern=Pattern.compile(patternBuilder.toString());
	}
	
	private class AnimationFinishCallback implements Runnable
	{
		@Override public void run() 
		{ mapAnimationStage=AnimationStage.NOT_ANIMATING; }
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
		setClickable(true);
		setLongClickable(true);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setBuiltInZoomControls(false);
		setSatellite(true);
		getController().setZoom(14);
		Context context=getContext();
		float densityFactor=context.getResources().getDisplayMetrics().density;
		numPixelsZoomLevel=Math.round(NUM_DIP_ZOOM_LEVEL*densityFactor);
		numPixelsScroll=Math.round(NUM_DIP_SCROLL*densityFactor);
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
}
