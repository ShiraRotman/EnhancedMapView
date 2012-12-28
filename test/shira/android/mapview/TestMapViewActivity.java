package shira.android.mapview;

import java.util.Map;
import java.util.HashMap;

import shira.android.mapview.EnhancedMapView.ControlAlignment;
import shira.android.mapview.EnhancedMapView.ControlType;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout.LayoutParams;

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
		//enhancedMapView.setRotation(180);
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
				testZoomControl();
				testRotationAnimation();
			}
		};
		enhancedMapView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
		RotationView rotationView=new RotationView(this);
		rotationView.addView(enhancedMapView);
		FrameLayout rootLayout=new FrameLayout(this);
		rootLayout.addView(rotationView);
		Button testButton=new Button(this);
		testButton.setText("Test");
		testButton.setOnClickListener(new View.OnClickListener() 
		{
			@Override public void onClick(View view) 
			{
				enhancedMapView.getRotationController().animateRotation(45,
						false,null,5000);
			}
		});
		ViewGroup.LayoutParams layoutParams=new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.
				WRAP_CONTENT);
		rootLayout.addView(testButton,layoutParams);
		setContentView(rootLayout);
		//Log.i("MapView","Rotating");
		/*rotationView.setPivotX(rotationView.getWidth()/2);
		rotationView.setPivotY(rotationView.getHeight()/2);*/
		//rotationView.setRotationDegrees(45);
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
	
	private void testRotationAnimation()
	{ 
		enhancedMapView.getRotationController().animateRotation(225,true,
				null,10000);
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
	
	private void testZoomControl()
	{
		Map<String,Object> properties=new HashMap<String,Object>();
		properties.put(MapControlDefsUtils.CONTROL_STYLE_PROP_KEY,
				MapControlDefsUtils.ZoomControlStyle.LARGE);
		enhancedMapView.setMapControl(ControlType.ZOOM,properties);
	}
}
