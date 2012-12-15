package shira.android.mapview;

import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.util.Log;
import android.view.*;
import android.widget.*;

import shira.android.mapview.MapControlDefsUtils.MapType;
import shira.android.mapview.MapControlDefsUtils.MapTypeControlStyle;

class MapTypeControlBuilder implements MapControlBuilder 
{
	private static final int DROPDOWN_WIDTH=135;
	private static final int DROPDOWN_HEIGHT=40;
	private static final int BAR_BUTTON_WIDTH=75;
	private static final int BAR_BUTTON_HEIGHT=35;
	private static final int MARGIN_OFFSET=-5;
	
	private static class ToggleButtonClickListener implements View.OnClickListener
	{
		@Override public void onClick(View view) 
		{
			ToggleButton toggleButton=(ToggleButton)view;
			if (toggleButton.isChecked())
			{
				ViewGroup parent=(ViewGroup)toggleButton.getParent();
				int numChildren=parent.getChildCount();
				for (int counter=0;counter<numChildren;counter++)
				{
					View childView=parent.getChildAt(counter);
					if (childView!=view) 
						((ToggleButton)childView).setChecked(false);
				}
			}
			else toggleButton.setChecked(true);
		}
	}
	
	public MapTypeControlBuilder() { }
	
	@Override
	public View buildControl(Map<String,Object> properties,View existingControl,
			EnhancedMapView mapView)
	{
		MapControlDefsUtils.validateControlProperties(properties);
		MapTypeControlStyle controlStyle=(MapTypeControlStyle)MapControlDefsUtils.
				getControlStyle(properties);
		MapType[] allowedMapTypes;
		Set<MapType> allowedMapTypesSet=(Set<MapType>)properties.get(
				MapControlDefsUtils.ALLOWED_MAP_TYPES_PROP_KEY);
		if (allowedMapTypesSet!=null)
		{
			allowedMapTypes=allowedMapTypesSet.toArray(new MapType
					[allowedMapTypesSet.size()]);
		}
		else allowedMapTypes=MapType.values();
		View control=null;
		switch (controlStyle)
		{
			case DROPDOWN_MENU:
				Spinner dropdownControl;
				if (existingControl instanceof Spinner) 
					dropdownControl=(Spinner)existingControl;
				else
				{
					Context controlContext=MapControlDefsUtils.extractContext(
							existingControl,mapView);
					dropdownControl=new Spinner(controlContext,Spinner.
							MODE_DROPDOWN);
				}
				ArrayAdapter<MapType> adapter=new ArrayAdapter<MapType>(
						dropdownControl.getContext(),android.R.layout.
						simple_spinner_item,allowedMapTypes);
						//R.layout.spinner_item
				adapter.setDropDownViewResource(android.R.layout.
						simple_spinner_dropdown_item);
				dropdownControl.setAdapter(adapter);
				//dropdownControl.setGravity(Gravity.LEFT | Gravity.TOP);
				if (mapView!=null)
				{
					//For more map types move to a separate function
					MapType mapType=(mapView.isSatellite()?MapType.SATELLITE:
							MapType.ROADMAP);
					updateDropDownSelection(dropdownControl,mapType);
				}
				control=dropdownControl;
				break;
			case HORIZONTAL_BAR:
				//Refactor to a custom view?
				LinearLayout buttonsBarControl;
				Context controlContext;
				if (existingControl instanceof LinearLayout)
				{
					buttonsBarControl=(LinearLayout)existingControl;
					buttonsBarControl.removeAllViews();
					controlContext=buttonsBarControl.getContext();
				}
				else
				{
					controlContext=MapControlDefsUtils.extractContext(
							existingControl,mapView);
					buttonsBarControl=new LinearLayout(controlContext);
				}
				buttonsBarControl.setOrientation(LinearLayout.HORIZONTAL);
				buttonsBarControl.setHorizontalGravity(Gravity.LEFT);
				buttonsBarControl.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
				int buttonWidth=Math.round(BAR_BUTTON_WIDTH*MapControlDefsUtils.
						densityFactor);
				int buttonHeight=Math.round(BAR_BUTTON_HEIGHT*MapControlDefsUtils.
						densityFactor);
				LinearLayout.LayoutParams layoutParams=new LinearLayout.
						LayoutParams(buttonWidth,buttonHeight);
				int margin=Math.round(MARGIN_OFFSET*MapControlDefsUtils.
						densityFactor);
				Log.i("MapView","Margin: " + margin);
				layoutParams.setMargins(margin,0,margin,0);
				View.OnClickListener listener=new ToggleButtonClickListener();
				for (MapType mapType:allowedMapTypes)
				{
					ToggleButton toggleButton=new ToggleButton(controlContext);
					toggleButton.setTextOn(mapType.title());
					toggleButton.setTextOff(mapType.title());
					toggleButton.setTextSize(10);
					toggleButton.setSingleLine(true);
					/*toggleButton.setBackgroundResource(android.R.drawable.
							btn_default);*/
					toggleButton.setBackgroundResource(R.drawable.toggle_button);
					/*toggleButton.setTextAppearance(controlContext,android.R.
							style.Widget_Button_Toggle);*/
					toggleButton.setTag(mapType);
					toggleButton.setOnClickListener(listener);
					buttonsBarControl.addView(toggleButton,layoutParams);
				}
				if (mapView!=null)
				{
					MapType mapType=(mapView.isSatellite()?MapType.SATELLITE:
							MapType.ROADMAP);
					updateToggleButtonsState(buttonsBarControl,mapType);
				}
				control=buttonsBarControl;
				break;
		} //end switch
		return control;
	}
	
	@Override 
	public void registerListeners(EnhancedMapView mapView,View control) 
	{
		if (control instanceof Spinner)
			registerDropDownListeners(mapView,(Spinner)control);
		else if (control instanceof LinearLayout)
			registerButtonsBarListeners(mapView,(LinearLayout)control);
	}
	
	private void registerDropDownListeners(final EnhancedMapView mapView,
			final Spinner dropdownControl)
	{
		dropdownControl.setOnItemSelectedListener(new AdapterView.
				OnItemSelectedListener() 
		{
			@Override
			public void onItemSelected(AdapterView<?> parent,View view,
					int position,long id) 
			{
				MapType mapType=(MapType)parent.getItemAtPosition(position);
				/*If more map types are added in the future, the code that 
				 *operates on the map view according to the map type which was 
				 *selected may become more complex, and should be moved to a  
				 *separate function because all the listeners for the various 
				 *control styles use the same code too.*/
				boolean isSatellite=(mapType==MapType.SATELLITE);
				mapView.setSatellite(isSatellite,false);
			}

			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
		MapViewChangeListener listener=new AbstractMapViewChangeListener() 
		{
			@Override
			public void onTileChange(EnhancedMapView mapView,boolean isSatellite) 
			{
				//For more map types move to a separate function  
				MapType mapType=(isSatellite?MapType.SATELLITE:MapType.ROADMAP);
				updateDropDownSelection(dropdownControl,mapType);
			}
		};
		mapView.addChangeListener(listener);
		dropdownControl.setTag(R.id.change_listener,listener);
	}
	
	private void updateDropDownSelection(Spinner dropdownControl,MapType mapType)
	{
		boolean found=false; int position=0;
		while (!found)
		{
			if (dropdownControl.getItemAtPosition(position)==mapType)
				found=true;
			else position++;
		}
		dropdownControl.setSelection(position);
	}
	
	private void registerButtonsBarListeners(final EnhancedMapView mapView,
			final LinearLayout buttonsBarControl)
	{
		CompoundButton.OnCheckedChangeListener listener=new CompoundButton.
				OnCheckedChangeListener() 
		{	
			@Override
			public void onCheckedChanged(CompoundButton buttonView,boolean 
					isChecked) 
			{
				if (buttonView.isChecked())
				{
					boolean isSatellite=(buttonView.getTag()==MapType.SATELLITE);
					mapView.setSatellite(isSatellite,false);
				}
			}
		};
		int numChildren=buttonsBarControl.getChildCount();
		for (int counter=0;counter<numChildren;counter++)
		{
			ToggleButton toggleButton=(ToggleButton)buttonsBarControl.
					getChildAt(counter);
			toggleButton.setOnCheckedChangeListener(listener);
		}
		MapViewChangeListener changeListener=new AbstractMapViewChangeListener() 
		{
			@Override
			public void onTileChange(EnhancedMapView mapView,boolean isSatellite) 
			{
				MapType mapType=(isSatellite?MapType.SATELLITE:MapType.ROADMAP);
				updateToggleButtonsState(buttonsBarControl,mapType);
			}
		};
		mapView.addChangeListener(changeListener);
		buttonsBarControl.setTag(R.id.change_listener,changeListener);
	}
	
	private void updateToggleButtonsState(LinearLayout buttonsBarControl,
			MapType mapType)
	{
		int numChildren=buttonsBarControl.getChildCount();
		for (int counter=0;counter<numChildren;counter++)
		{
			ToggleButton toggleButton=(ToggleButton)buttonsBarControl.
					getChildAt(counter);
			toggleButton.setChecked(toggleButton.getTag()==mapType);
		}
	}
	
	@Override public int getMinimumWidth(Map<String,Object> properties)
	{
		MapControlDefsUtils.validateControlProperties(properties);
		MapTypeControlStyle controlStyle=(MapTypeControlStyle)MapControlDefsUtils.
				getControlStyle(properties);
		int minimumWidth=0;
		switch (controlStyle)
		{
			case DROPDOWN_MENU: minimumWidth=DROPDOWN_WIDTH; break;
			case HORIZONTAL_BAR:
				int numMapTypes;
				Set<MapType> allowedMapTypes=(Set<MapType>)properties.get(
						MapControlDefsUtils.ALLOWED_MAP_TYPES_PROP_KEY);
				if (allowedMapTypes!=null) numMapTypes=allowedMapTypes.size();
				else numMapTypes=MapType.values().length;
				minimumWidth=(BAR_BUTTON_WIDTH+2*MARGIN_OFFSET)*numMapTypes;
				break;
		}
		Log.i("MapView","Width: " + minimumWidth);
		return Math.round(minimumWidth*MapControlDefsUtils.densityFactor);
	}
	
	@Override public int getMinimumHeight(Map<String,Object> properties) 
	{
		MapControlDefsUtils.validateControlProperties(properties);
		MapTypeControlStyle controlStyle=(MapTypeControlStyle)MapControlDefsUtils.
				getControlStyle(properties);
		int minimumHeight=0;
		switch (controlStyle)
		{
			case DROPDOWN_MENU: minimumHeight=DROPDOWN_HEIGHT; break;
			case HORIZONTAL_BAR: minimumHeight=BAR_BUTTON_HEIGHT; break;
		}
		return Math.round(minimumHeight*MapControlDefsUtils.densityFactor);
	}
	
	@Override public EnhancedMapView.ControlAlignment getDefaultAlignment()
	{ return EnhancedMapView.ControlAlignment.TOP_RIGHT; }
}
