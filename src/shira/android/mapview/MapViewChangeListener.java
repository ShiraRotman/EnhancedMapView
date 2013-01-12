package shira.android.mapview;

import com.google.android.maps.GeoPoint;

public interface MapViewChangeListener 
{
	public void onPan(EnhancedMapView mapView,GeoPoint oldCenter,GeoPoint newCenter);
	public void onZoom(EnhancedMapView mapView,int oldZoomLevel,int newZoomLevel);
	public void onRotate(EnhancedMapView mapView,float oldDegrees,float newDegrees);
	/*In the future more tile types may be supported, requiring the second 
	 *parameter to change in definition and the previous type to also be supplied*/
	public void onTileChange(EnhancedMapView mapView,boolean isSatellite);
}
