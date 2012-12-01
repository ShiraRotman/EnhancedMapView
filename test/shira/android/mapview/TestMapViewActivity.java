package shira.android.mapview;

import android.os.Bundle;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;

public class TestMapViewActivity extends MapActivity 
{
	private EnhancedMapView enhancedMapView;
	
	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		String apiKey=getResources().getString(R.string.google_maps_api_key);
		enhancedMapView=new EnhancedMapView(this,apiKey);
		setContentView(enhancedMapView);
		testChangeListener();
	}
	
	@Override protected boolean isRouteDisplayed() { return false; }
	
	private void testChangeListener()
	{
		enhancedMapView.setMapViewChangeListener(new MapViewChangeListener() 
		{	
			@Override 
			public void onZoom(EnhancedMapView mapView,int oldZoomLevel,
					int newZoomLevel) 
			{ Log.i("Test","Zoom"); }
			
			@Override
			public void onPan(EnhancedMapView mapView,GeoPoint oldCenter,
					GeoPoint newCenter) 
			{ Log.i("Test","Pan"); }
		});
	}
}
