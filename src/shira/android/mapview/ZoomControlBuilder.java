package shira.android.mapview;

import com.google.android.maps.MapController;
import java.util.Map;

import android.content.Context;
//import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import shira.android.mapview.MapControlDefsUtils.ZoomControlStyle;

public class ZoomControlBuilder implements MapControlBuilder 
{
	private static final int ZOOM_BUTTON_WIDTH=28;
	private static final int ZOOM_BUTTON_HEIGHT=45;
	private static final int SLIDER_WIDTH=30;
	private static final int SLIDER_HEIGHT=100;
	private static final int MARGIN_OFFSET=-4;
	
	private static class ZoomButtonClickListener implements View.OnClickListener
	{
		private EnhancedMapView mapView;
		private int difference;
		
		public ZoomButtonClickListener(EnhancedMapView mapView,int difference) 
		{ this.mapView=mapView; this.difference=difference; }
		
		//public int getDifference() { return difference; }
		
		@Override public void onClick(View view) 
		{
			MapController controller=mapView.getController();
			switch (difference)
			{
				case 1: controller.zoomIn(); break;
				case -1: controller.zoomOut(); break;
				default: 
					controller.setZoom(mapView.getZoomLevel()+difference);
					break;
			}
		}
	}
	
	public ZoomControlBuilder() { }
	
	@Override
	public View buildControl(Map<String,Object> properties,View existingControl,
			EnhancedMapView mapView)
	{
		MapControlDefsUtils.validateControlProperties(properties);
		ZoomControlStyle controlStyle=(ZoomControlStyle)MapControlDefsUtils.
				getControlStyle(properties);
		LinearLayout control;
		if (existingControl instanceof LinearLayout)
		{
			/*The assumption here is that the supplied control has indeed been 
			 *created by this builder, and is composed of the correct sub 
			 *controls, otherwise the result will also be incorrect, and the 
			 *control might look strange and unarranged.*/
			control=(LinearLayout)existingControl;
			int numChildControls=control.getChildCount();
			if ((controlStyle==ZoomControlStyle.SMALL)&&(numChildControls!=2))
			{
				control.removeViewAt(1);
				int margin=Math.round(MARGIN_OFFSET*MapControlDefsUtils.
						densityFactor);
				updateButtonsLayoutParams(control,margin);
			}
			else if ((controlStyle==ZoomControlStyle.LARGE)&&(numChildControls!=3))
			{
				updateButtonsLayoutParams(control,0);
				SeekBar slider=buildZoomSlider(control.getContext());
				control.addView(slider,1);
			}
			/*If neither of the above conditions are met, the supplied control 
			 *is necessarily not one that has been created by this builder, and 
			 *thus it's unchanged (the opposite is not true).*/
		} //end if
		else
		{
			if (mapView==null)
				throw new NullPointerException("Neither an existing control " + 
						"nor a map view to take the context for the built " + 
						"control from were supplied!");
			Context context=mapView.getContext();
			control=new LinearLayout(context);
			control.setOrientation(LinearLayout.VERTICAL);
			//control.setHorizontalGravity(Gravity.CENTER);
			int margin=(controlStyle==ZoomControlStyle.SMALL?Math.round(
					MARGIN_OFFSET*MapControlDefsUtils.densityFactor):0);
			LinearLayout.LayoutParams layoutParams=new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,ZOOM_BUTTON_HEIGHT);
			layoutParams.setMargins(0,margin,0,margin);
			Button zoomButton=new Button(context);
			zoomButton.setText("+"); zoomButton.setTextSize(10);
			control.addView(zoomButton,layoutParams); 
			if (controlStyle==ZoomControlStyle.LARGE)
				control.addView(buildZoomSlider(context));
			zoomButton=new Button(context);
			zoomButton.setText("-"); zoomButton.setTextSize(10);
			control.addView(zoomButton,layoutParams);
		} //end else
		if ((controlStyle==ZoomControlStyle.LARGE)&&(mapView!=null))
		{
			SeekBar slider=(SeekBar)control.getChildAt(1);
			slider.setProgress(mapView.getZoomLevel()-1);
		}
		return control;
	}
	
	private static void updateButtonsLayoutParams(LinearLayout control,int margin)
	{
		LinearLayout.LayoutParams layoutParams=(LinearLayout.
				LayoutParams)control.getChildAt(0).getLayoutParams();
		layoutParams.setMargins(0,margin,0,margin);
		control.getChildAt(0).setLayoutParams(layoutParams);
		control.getChildAt(control.getChildCount()-1).setLayoutParams(
				layoutParams);
	}
	
	private static SeekBar buildZoomSlider(Context context)
	{
		ExtendedSeekBar slider=new ExtendedSeekBar(context);
		slider.setOrientation(ExtendedSeekBar.Orientation.VERTICAL);
		slider.setMax(20); slider.setProgress(0); /*slider.setRotation(270);
		slider.setTranslationX(-SLIDER_HEIGHT); slider.setTranslationY(0);*/
		//slider.setPivotX(0); slider.setPivotY(0);
		slider.setProgressDrawable(context.getResources().getDrawable(
				R.drawable.zoom_slider));
		LinearLayout.LayoutParams layoutParams=new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,SLIDER_HEIGHT);
		slider.setLayoutParams(layoutParams);
		return slider;
	}
	
	@Override 
	public void registerListeners(final EnhancedMapView mapView,View control) 
	{
		ViewGroup containerControl=(ViewGroup)control;
		int numChildren=containerControl.getChildCount();
		View childControl=containerControl.getChildAt(0);
		if (childControl.getTag(R.id.control_listener)==null)
		{
			View.OnClickListener listener=new ZoomButtonClickListener(mapView,1);
			childControl.setOnClickListener(listener);
			childControl.setTag(R.id.control_listener,listener);
			childControl=containerControl.getChildAt(numChildren-1);
			listener=new ZoomButtonClickListener(mapView,-1);
			childControl.setOnClickListener(listener);
			childControl.setTag(R.id.control_listener,listener);
		}
		Object changeListenerObject=control.getTag(R.id.change_listener);
		if ((numChildren==3)&&(changeListenerObject==null))
		{
			final SeekBar slider=(SeekBar)containerControl.getChildAt(1);
			SeekBar.OnSeekBarChangeListener sliderListener=new SeekBar.
					OnSeekBarChangeListener() 
			{
				@Override public void onStopTrackingTouch(SeekBar seekBar) 
				{
					MapController controller=mapView.getController();
					controller.setZoom(seekBar.getProgress()+1);
				}
				
				@Override public void onStartTrackingTouch(SeekBar seekBar) { }
				
				@Override
				public void onProgressChanged(SeekBar seekBar,int progress,
						boolean fromUser) { }
			};
			slider.setOnSeekBarChangeListener(sliderListener);
			slider.setTag(R.id.control_listener,sliderListener);
			MapViewChangeListener changeListener=new AbstractMapViewChangeListener()
			{
				@Override 
				public void onZoom(EnhancedMapView mapView,int oldZoomLevel,
						int newZoomLevel) 
				{ slider.setProgress(newZoomLevel-1); }
			};
			mapView.addChangeListener(changeListener);
			control.setTag(R.id.change_listener,changeListener);
		} //end if numChildren...
		else if ((numChildren==2)&&(changeListenerObject!=null))
		{
			MapViewChangeListener changeListener=(MapViewChangeListener)
					changeListenerObject;
			mapView.removeChangeListener(changeListener);
			control.setTag(R.id.change_listener,null);
		}
	}
	
	@Override public int getMinimumWidth(Map<String,Object> properties)
	{ 
		return Math.round(Math.max(ZOOM_BUTTON_WIDTH,SLIDER_WIDTH)*
				MapControlDefsUtils.densityFactor);
	}
	
	@Override public int getMinimumHeight(Map<String,Object> properties)
	{
		MapControlDefsUtils.validateControlProperties(properties);
		ZoomControlStyle controlStyle=(ZoomControlStyle)MapControlDefsUtils.
				getControlStyle(properties);
		int minimumHeight=0;
		switch (controlStyle)
		{
			case SMALL: 
				minimumHeight=(ZOOM_BUTTON_HEIGHT+2*MARGIN_OFFSET)*2;
				break;
			case LARGE: 
				minimumHeight=SLIDER_HEIGHT+ZOOM_BUTTON_HEIGHT*2;
				break;
		}
		return Math.round(minimumHeight*MapControlDefsUtils.densityFactor);
	}
	
	@Override public EnhancedMapView.ControlAlignment getDefaultAlignment()
	{ return EnhancedMapView.ControlAlignment.TOP_LEFT; }
}
