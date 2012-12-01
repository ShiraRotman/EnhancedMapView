package shira.android.mapview;

import com.google.android.maps.GeoPoint;

public interface MapViewChangeListener 
{
	public void onPan(EnhancedMapView mapView,GeoPoint oldCenter,GeoPoint newCenter);
	public void onZoom(EnhancedMapView mapView,int oldZoomLevel,int newZoomLevel);
}
