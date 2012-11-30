package shira.android.mapview;

import android.view.MotionEvent;
import com.google.android.maps.GeoPoint;

public abstract class AbstractMapViewGestureListener implements 
		MapViewGestureListener 
{
	@Override 
	public void onSingleTap(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent) { }

	@Override
	public void onDoubleTap(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent) { }

	@Override
	public void onLongPress(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent) { }

	@Override
	public void onScroll(EnhancedMapView mapView,GeoPoint topLeft,GeoPoint 
			bottomRight,MotionEvent motionEvent1,MotionEvent motionEvent2,
			float distanceX, float distanceY) { }
}
