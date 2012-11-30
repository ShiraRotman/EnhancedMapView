package shira.android.mapview;

import android.view.MotionEvent;
import com.google.android.maps.GeoPoint;

public interface MapViewGestureListener 
{
	public void onSingleTap(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent);
	public void onDoubleTap(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent);
	public void onLongPress(EnhancedMapView mapView,GeoPoint geoPoint,
			MotionEvent motionEvent);
	public void onScroll(EnhancedMapView mapView,GeoPoint topLeft,GeoPoint 
			bottomRight,MotionEvent motionEvent1,MotionEvent motionEvent2,
			float distanceX,float distanceY);
}
