package shira.android.mapview;

import com.google.android.maps.GeoPoint;

public abstract class AbstractMapViewChangeListener implements 
		MapViewChangeListener 
{
	@Override
	public void onPan(EnhancedMapView mapView,GeoPoint oldCenter,GeoPoint 
			newCenter) { }

	@Override public void onZoom(EnhancedMapView mapView,int oldZoomLevel,
			int newZoomLevel) { }

	@Override 
	public void onTileChange(EnhancedMapView mapView,boolean isSatellite) { }
}
