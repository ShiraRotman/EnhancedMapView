package shira.android.mapview;

import android.view.View;
import java.util.Map;

interface MapControlBuilder 
{
	public abstract View buildControl(Map<String,Object> properties,View 
			existingControl);
	public abstract void registerListeners(EnhancedMapView mapView,View control);
	public abstract int getMinimumWidth(Map<String,Object> properties);
	public abstract int getMinimumHeight(Map<String,Object> properties);
	public abstract EnhancedMapView.ControlAlignment getDefaultAlignment();
}
