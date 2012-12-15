package shira.android.mapview;

import java.util.Map;
import java.util.HashMap;

import shira.android.mapview.EnhancedMapView.ControlAlignment;
import shira.android.mapview.EnhancedMapView.ControlType;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;

public class TestMapViewActivity extends MapActivity 
{
	private EnhancedMapView enhancedMapView;
	
	private class DummyControlBuilder implements MapControlBuilder
	{
		@Override
		public void registerListeners(EnhancedMapView mapView,View control) 
		{ }
		
		@Override public ControlAlignment getDefaultAlignment() 
		{ return ControlAlignment.TOP_LEFT; }
		
		@Override
		public View buildControl(Map<String, Object> properties,View 
				existingControl,EnhancedMapView mapView) 
		{ return new Button(TestMapViewActivity.this); }

		@Override public int getMinimumWidth(Map<String,Object> properties) 
		{ return 50; }

		@Override public int getMinimumHeight(Map<String,Object> properties) 
		{ return 30; }
	}
	
	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		String apiKey=getResources().getString(R.string.google_maps_api_key);
		enhancedMapView=new EnhancedMapView(this,apiKey);
		/*DummyControlBuilder dummyBuilder=new DummyControlBuilder();
		for (ControlType controlType:ControlType.values())
			EnhancedMapView.controlBuilders.put(controlType,dummyBuilder);*/
		ViewTreeObserver.OnGlobalLayoutListener listener=new 
				ViewTreeObserver.OnGlobalLayoutListener()
		{
			@Override public void onGlobalLayout()
			{
				enhancedMapView.getViewTreeObserver().removeGlobalOnLayoutListener(
						this);
				testMapTypeControl();
			}
		};
		enhancedMapView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
		setContentView(enhancedMapView);
		//testChangeListener();
	}
	
	@Override protected boolean isRouteDisplayed() { return false; }
	
	private void testChangeListener()
	{
		enhancedMapView.addChangeListener(new MapViewChangeListener() 
		{	
			@Override 
			public void onZoom(EnhancedMapView mapView,int oldZoomLevel,
					int newZoomLevel) 
			{ Log.i("Test","Zoom"); }
			
			@Override
			public void onPan(EnhancedMapView mapView,GeoPoint oldCenter,
					GeoPoint newCenter) 
			{ Log.i("Test","Pan"); }

			@Override 
			public void onTileChange(EnhancedMapView mapView,boolean isSatellite) { }
		});
	}
	
	private void testMapControlBasic()
	{
		enhancedMapView.setMapControl(ControlType.MAP_TYPE,null);
		enhancedMapView.setMapControl(ControlType.PAN,null,ControlAlignment.
				BOTTOM_CENTER,View.VISIBLE);
		enhancedMapView.setMapControl(ControlType.ROTATE,null);
		enhancedMapView.setMapControl(ControlType.ZOOM,null);
		enhancedMapView.setMapControl(ControlType.ROTATE,null,ControlAlignment.
				LEFT_TOP,View.VISIBLE);
	}
	
	private void testMapControlCenter()
	{
		ControlAlignment alignment=ControlAlignment.RIGHT_CENTER;
		enhancedMapView.setMapControl(ControlType.PAN,null,alignment,View.VISIBLE);
		enhancedMapView.setMapControl(ControlType.ROTATE,null,alignment,View.VISIBLE);
		enhancedMapView.setMapControl(ControlType.ZOOM,null,alignment,View.VISIBLE);
		//enhancedMapView.setMapControl(ControlType.PAN,null,alignment,View.GONE);
		enhancedMapView.setMapControl(ControlType.MAP_TYPE,null,alignment,View.VISIBLE);
		enhancedMapView.setMapControl(ControlType.ROTATE,null,alignment,View.GONE);
	}
	
	private void testMapTypeControl()
	{ 
		Map<String,Object> properties=new HashMap<String,Object>();
		properties.put(MapControlDefsUtils.CONTROL_STYLE_PROP_KEY,
				MapControlDefsUtils.MapTypeControlStyle.HORIZONTAL_BAR);
		enhancedMapView.setMapControl(ControlType.MAP_TYPE,properties);
				//ControlAlignment.LEFT_TOP,View.VISIBLE);
	}
}
