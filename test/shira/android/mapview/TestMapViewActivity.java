package shira.android.mapview;

import android.os.Bundle;
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
	}
	
	@Override protected boolean isRouteDisplayed() { return false; } 
}
