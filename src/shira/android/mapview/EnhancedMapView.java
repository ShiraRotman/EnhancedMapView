package shira.android.mapview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.google.android.maps.*;
import java.util.Iterator;

public class EnhancedMapView extends MapView
{
	private static final int NUM_DIP_ZOOM_LEVEL=5;
	
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
	//private MotionEvent downMotionEvent,scrollMotionEvent;
	private MapViewGestureListener gestureListener;
	private AnimationFinishCallback animCallback=new AnimationFinishCallback();
	private GestureKind ongoingGestureKind=GestureKind.UNDETERMINED;
	private AnimationStage mapAnimationStage=AnimationStage.NOT_ANIMATING;
	private int pointerID=-1,numPixelsZoomLevel;
	private boolean useDefaultGestures=true,isHandlingScale=true;
	
	private enum GestureKind { UNDETERMINED,SCROLL,LONG_PRESS,SCALE }; 
	private enum AnimationStage { NOT_ANIMATING,ANIMATING,INTERRUPTED };
	
	private class TouchGestureListener extends GestureDetector.
			SimpleOnGestureListener implements ScaleGestureDetector.
			OnScaleGestureListener
	{
		@Override public boolean onSingleTapUp(MotionEvent motionEvent)
		{ 
			handleSingleTap(motionEvent);
			return true;
		}
		
		@Override public boolean onDoubleTap(MotionEvent motionEvent)
		{
			handleDoubleTap(motionEvent);
			return true;
		}
		
		@Override public void onLongPress(MotionEvent motionEvent)
		{
			ongoingGestureKind=GestureKind.LONG_PRESS;
			MotionEvent cancelMotionEvent=createCancelGestureEvent(
					motionEvent);
			touchScrollDetector.onTouchEvent(cancelMotionEvent);
			if ((useDefaultGestures)&&(isHandlingScale))
				touchScaleDetector.onTouchEvent(cancelMotionEvent);
			handleLongPress(motionEvent);
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
			
		}
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
		setBuiltInZoomControls(false);
		setSatellite(true);
		getController().setZoom(14);
		Context context=getContext();
		float densityFactor=context.getResources().getDisplayMetrics().density;
		numPixelsZoomLevel=Math.round(NUM_DIP_ZOOM_LEVEL*densityFactor);
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
	
	public boolean areDefaultGestures() { return useDefaultGestures; }
	public void useDefaultGestures(boolean useDefaultGestures)
	{ this.useDefaultGestures=useDefaultGestures; }
	
	public MapViewGestureListener getMapViewGestureListener()
	{ return gestureListener; }
	public void setMapViewGestureListener(MapViewGestureListener listener)
	{ gestureListener=listener; }
	
	@Override public boolean onTouchEvent(MotionEvent motionEvent)
	{
		if ((!isEnabled())||(!isClickable())) return false;
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
				if (useDefaultGestures) 
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
					if ((useDefaultGestures)&&(isHandlingScale))
						touchScaleDetector.onTouchEvent(cancelMotionEvent);
				}
				updateGestureDetector(motionEvent);
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				if ((ongoingGestureKind==GestureKind.UNDETERMINED)&&
						(useDefaultGestures))
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
					if ((useDefaultGestures)&&(isHandlingScale))
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
	
	private void handleSingleTap(MotionEvent motionEvent)
	{
		Log.i("MapView","Single Tap");
		if (mapAnimationStage==AnimationStage.INTERRUPTED)
			mapAnimationStage=AnimationStage.NOT_ANIMATING;
		else
		{
			GeoPoint tapGeoPoint=getTouchGeoPoint(motionEvent);
			if (tapGeoPoint!=null)
			{
				boolean consumed=false;
				Iterator<Overlay> overlaysIterator=getOverlays().iterator();
				while ((!consumed)&&(overlaysIterator.hasNext()))
					consumed=overlaysIterator.next().onTap(tapGeoPoint,this);
				if ((!consumed)&&(gestureListener!=null))
					gestureListener.onSingleTap(this,tapGeoPoint,motionEvent);
			}
		}
	}
	
	private void handleDoubleTap(MotionEvent motionEvent)
	{
		Log.i("MapView","Double Tap, " + mapAnimationStage);
		GeoPoint tapGeoPoint=getTouchGeoPoint(motionEvent);
		if (tapGeoPoint==null) return;
		boolean consumed=false;
		switch (mapAnimationStage)
		{
			case NOT_ANIMATING:
				//TODO: Handle overlays with a double tap
				if ((!consumed)&&(useDefaultGestures))
				{
					Log.i("MapView","Animating");
					mapAnimationStage=AnimationStage.ANIMATING;
					getController().animateTo(tapGeoPoint,animCallback);
					consumed=true;
				}
				break;
			case INTERRUPTED:
				mapAnimationStage=AnimationStage.NOT_ANIMATING;
				/*A double tap during animation is considered a single tap if a
				 *layer chooses to handle it*/ 
				Iterator<Overlay> overlaysIterator=getOverlays().iterator();
				while ((!consumed)&&(overlaysIterator.hasNext()))
					consumed=overlaysIterator.next().onTap(tapGeoPoint,this);
				break;
		}
		if ((!consumed)&&(gestureListener!=null))
			gestureListener.onDoubleTap(this,tapGeoPoint,motionEvent);
	}
	
	private void handleLongPress(MotionEvent motionEvent)
	{
		if (mapAnimationStage==AnimationStage.INTERRUPTED)
			mapAnimationStage=AnimationStage.NOT_ANIMATING;
		else
		{
			GeoPoint pressGeoPoint=getTouchGeoPoint(motionEvent);
			if (pressGeoPoint!=null)
			{
				boolean consumed=false;
				//TODO: Handle overlays
				if ((!consumed)&&(gestureListener!=null))
					gestureListener.onLongPress(this,pressGeoPoint,motionEvent);
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
		if (useDefaultGestures)
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
}
