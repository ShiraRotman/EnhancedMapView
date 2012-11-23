package shira.android.mapview;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.maps.MapView;

public class EnhancedMapView extends MapView
{	
	public EnhancedMapView(Context context,String apiKey)
	{ 
		super(context,apiKey);
		initialize();
	}
	
	public EnhancedMapView(Context context,AttributeSet attrs)
	{ this(context,attrs,0); }
	
	public EnhancedMapView(Context context,AttributeSet attrs,int defStyle)
	{ 
		super(context,attrs,defStyle);
		initialize();
	}
	
	private void initialize() { setClickable(true); }
}
