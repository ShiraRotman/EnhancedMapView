package shira.android.mapview;

import java.util.Map;
import android.content.Context;
import android.view.View;

public final class MapControlDefsUtils 
{
	public static final String CONTROL_STYLE_PROP_KEY="control_style";
	public static final String ALLOWED_MAP_TYPES_PROP_KEY="allowed_map_types";
	static float densityFactor;
	
	public enum MapTypeControlStyle { DROPDOWN_MENU,HORIZONTAL_BAR };
	public enum ZoomControlStyle { SMALL,LARGE };
	
	public enum MapType 
	{ 
		ROADMAP ("Road map"),SATELLITE ("Satellite");
		
		private final String title;
		MapType(String title) { this.title=title; }
		String title() { return title; }
		
		@Override public String toString() { return title; }
	};
	
	private MapControlDefsUtils() { }
	
	static final void validateControlProperties(Map<String,Object> 
			properties)
	{
		if (properties==null)
			throw new NullPointerException("A properties map for the control " + 
					"must be supplied!");
	}
	
	static final Object getControlStyle(Map<String,Object> properties)
	{
		Object controlStyle=properties.get(CONTROL_STYLE_PROP_KEY);
		if (controlStyle==null)
			throw new IllegalArgumentException("The properties map supplied " +
					"does not contain the style of the control!");
		return controlStyle;
	}
	
	static final Context extractContext(View control,EnhancedMapView mapView)
	{
		Context resultContext;
		if (control!=null) resultContext=control.getContext();
		else if (mapView!=null) resultContext=mapView.getContext();
		else
			throw new IllegalArgumentException("Either a control or a map " + 
					"view to take the context from must be supplied!");
		return resultContext;
	}
}
