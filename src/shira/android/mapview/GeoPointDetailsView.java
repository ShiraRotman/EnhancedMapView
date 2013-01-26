package shira.android.mapview;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TabHost;
//import android.widget.TabWidget;
import android.widget.TextView;

import com.google.android.maps.OverlayItem;

public class GeoPointDetailsView extends FrameLayout
{
	private static final String SINGLE_MODE_ERROR_MESSAGE="This details " + 
			"bubble uses a single view to show its information! To allow " +
			"multiple views, set the multi mode to true.";
	private static final String CUSTOM_VIEW_ERROR_MESSAGE="Cannot get/set a " +
			"property of the standard view on a custom view!";
	
	public static final int DEFAULT_BACKGROUND_COLOR=0xFFFFFFFF;
	public static final float DEFAULT_CORNER_RADIUS=5;
	
	private static final String DETAILS_TAB_TAG_PREFIX="GEO";
	//private static final int TAB_LABEL_HEIGHT=30;
	private final float densityFactor;
	
	private LayoutInflater layoutInflater;
	private View singleDetailsView;
	private ExtendedTabHost multiDetailsTabs;
	private OverlayItem sourceOverlayItem;
	private GradientDrawable background;
	private int backgroundColor=DEFAULT_BACKGROUND_COLOR;
	private float cornerRadius=DEFAULT_CORNER_RADIUS;
	private boolean multiMode,initializing=true;
	
	public GeoPointDetailsView(Context context) { this(context,false); }
	
	public GeoPointDetailsView(Context context,boolean multiMode)
	{
		super(context);
		//setWillNotDraw(false);
		densityFactor=context.getResources().getDisplayMetrics().density;
		layoutInflater=(LayoutInflater)context.getSystemService(Context.
				LAYOUT_INFLATER_SERVICE);
		background=(GradientDrawable)context.getResources().getDrawable(
				R.drawable.geo_point_details);
		setBackgroundDrawable(background);
		setMultiMode(multiMode);
	}
	
	public boolean isMultiMode() { return multiMode; }
	
	public void setMultiMode(boolean multiMode)
	{
		if ((this.multiMode!=multiMode)||(initializing))
		{
			removeAllViews();
			if (multiMode)
			{
				if (multiDetailsTabs==null)
				{
					multiDetailsTabs=(ExtendedTabHost)layoutInflater.inflate(
							R.layout.geo_point_details_multi,this,false);
					multiDetailsTabs.setup();
					/*multiDetailsTabs.setTabLabelsHeight(Math.round(
							TAB_LABEL_HEIGHT*densityFactor));*/
				}
				addView(multiDetailsTabs);
			}
			else
			{
				if (singleDetailsView==null)
				{
					singleDetailsView=layoutInflater.inflate(R.layout.
							geo_point_details,this,false);
				}
				addView(singleDetailsView);
			}
			this.multiMode=multiMode;
			initializing=false;
		}
	}
	
	public int getBackgroundColor() { return backgroundColor; }
	
	public void setBackgroundColor(int backgroundColor)
	{
		background.setColor(backgroundColor);
		singleDetailsView.setBackgroundColor(backgroundColor);
		FrameLayout tabViewsContainer=multiDetailsTabs.getTabContentView();
		for (int index=0;index<tabViewsContainer.getChildCount();index++)
			tabViewsContainer.getChildAt(index).setBackgroundColor(backgroundColor);
	}
	
	public float getCornerRadius() { return cornerRadius; }
	
	public void setCornerRadius(float cornerRadius)
	{
		if (cornerRadius<0)
			throw new IllegalArgumentException("The corner radius must be at " +
					"least 0!");
		this.cornerRadius=cornerRadius;
		background.mutate();
		background.setCornerRadius(cornerRadius*densityFactor);
	}
	
	public int getNumDetailsTabs() 
	{
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		return multiDetailsTabs.getTabWidget().getChildCount();
	}
	
	private void validateModeTabIndex(int tabIndex)
	{
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		multiDetailsTabs.validateTabIndex(tabIndex);
	}
	
	public String getTitle()
	{
		if (multiMode) return getTitle(0);
		else return getTitle(singleDetailsView);
	}
	
	public String getTitle(int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		return getTitle(multiDetailsTabs.getTabContentView().getChildAt(tabIndex));
	}
	
	private String getTitle(View detailsView)
	{
		TextView titleTextView=(TextView)detailsView.findViewById(R.id.
				title_text_view);
		if (titleTextView!=null) return titleTextView.getText().toString();
		else throw new IllegalStateException(CUSTOM_VIEW_ERROR_MESSAGE);
	}
	
	public void setTitle(String title)
	{ 
		if (multiMode) setTitle(title,0); 
		else
		{
			setTitle(singleDetailsView,title);
			sourceOverlayItem=null;
		}
	}
	
	public void setTitle(String title,int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		setTitle(multiDetailsTabs.getTabContentView().getChildAt(tabIndex),title);
	}
	
	private void setTitle(View detailsView,String title)
	{
		TextView titleTextView=(TextView)detailsView.findViewById(R.id.
				title_text_view);
		if (titleTextView!=null) titleTextView.setText(title);
		else throw new IllegalStateException(CUSTOM_VIEW_ERROR_MESSAGE);
	}
	
	public String getSnippet()
	{
		if (multiMode) return getSnippet(0);
		else return getSnippet(singleDetailsView);
	}
	
	public String getSnippet(int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		return getSnippet(multiDetailsTabs.getTabContentView().getChildAt(tabIndex));
	}
	
	private String getSnippet(View detailsView)
	{
		TextView snippetTextView=(TextView)detailsView.findViewById(R.id.
				snippet_text_view);
		if (snippetTextView!=null) return snippetTextView.getText().toString();
		else throw new IllegalStateException(CUSTOM_VIEW_ERROR_MESSAGE);
	}
	
	public void setSnippet(String snippet)
	{ 
		if (multiMode) setSnippet(snippet,0); 
		else
		{
			setSnippet(singleDetailsView,snippet);
			sourceOverlayItem=null;
		}
	}
	
	public void setSnippet(String snippet,int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		setSnippet(multiDetailsTabs.getTabContentView().getChildAt(tabIndex),
				snippet);
	}
	
	private void setSnippet(View detailsView,String snippet)
	{
		TextView titleTextView=(TextView)detailsView.findViewById(R.id.
				snippet_text_view);
		if (titleTextView!=null) titleTextView.setText(snippet);
		else throw new IllegalStateException(CUSTOM_VIEW_ERROR_MESSAGE);
	}
	
	public View getDetailsView()
	{
		if (multiMode) return getDetailsView(0);
		else return singleDetailsView;
	}
	
	public View getDetailsView(int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		return multiDetailsTabs.getTabContentView().getChildAt(tabIndex);
	}
	
	public void setDetailsView(View detailsView)
	{
		if (multiMode) setDetailsView(null,detailsView,0);
		else if (detailsView==null)
			throw new NullPointerException("The details view must be non-null!");
		else 
		{ 
			singleDetailsView=detailsView;
			sourceOverlayItem=null;
		}
	}
	
	public void setDetailsView(String label,View detailsView,int tabIndex)
	{
		if (detailsView==null)
			throw new NullPointerException("The details view must be non-null!");
		validateModeTabIndex(tabIndex);
		/*TabWidget tabWidget=multiDetailsTabs.getTabWidget();
		tabWidget.removeViewAt(tabIndex);
		tabWidget.addView(detailsView,tabIndex);*/
		TabHost.TabSpec tabSpec=createTabSpec(label,detailsView,tabIndex);
		//multiDetailsTabs.replaceTab(tabSpec,tabIndex);
		multiDetailsTabs.removeTab(tabIndex);
		multiDetailsTabs.addTab(tabSpec,tabIndex);
	}
	
	public OverlayItem getSourceOverlayItem() 
	{
		if (multiMode)
			throw new IllegalStateException("An overlay item is relevant " +
					"only if the bubble shows a single view!");
		else return sourceOverlayItem; 
	}
	
	public void setSourceOverlayItem(OverlayItem sourceOverlayItem)
	{
		if (sourceOverlayItem==null)
			throw new NullPointerException("The overlay item from which to " +
					"take the data to display cannot be set to null! Use the " +
					"setter functions to change the data.");
		if (multiMode)
			throw new IllegalStateException("An overlay item can only be " +
					"used if the bubble shows a single view!");
		setTitle(sourceOverlayItem.getTitle());
		setSnippet(sourceOverlayItem.getSnippet());
		this.sourceOverlayItem=sourceOverlayItem;
	}
	
	public void addDetailsTab(String label,String title,String snippet)
	{
		/*The validation is needed here because if the component has never been 
		 *in multi mode, a NullPointerException will be thrown when trying to 
		 *get the number of tabs*/
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		addDetailsTab(label,title,snippet,multiDetailsTabs.getTabWidget().
				getChildCount());
	}
	
	public void addDetailsTab(String label,String title,String snippet,
			int tabIndex)
	{
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		View detailsView=layoutInflater.inflate(R.layout.geo_point_details,
				this,false);
		detailsView.setPadding(0,0,0,0);
		setTitle(detailsView,title);
		setSnippet(detailsView,snippet);
		TabHost.TabSpec tabSpec=createTabSpec(label,detailsView,tabIndex);
		multiDetailsTabs.addTab(tabSpec,tabIndex);
	}
	
	public void addDetailsTab(String label,View detailsView)
	{
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		addDetailsTab(label,detailsView,multiDetailsTabs.getTabWidget().
				getChildCount());
	}
	
	public void addDetailsTab(String label,View detailsView,int tabIndex)
	{
		if (!multiMode)
			throw new IllegalStateException(SINGLE_MODE_ERROR_MESSAGE);
		TabHost.TabSpec tabSpec=createTabSpec(label,detailsView,tabIndex);
		multiDetailsTabs.addTab(tabSpec,tabIndex);
	}
	
	private TabHost.TabSpec createTabSpec(String label,final View detailsView,
			int tabIndex)
	{
		TabHost.TabSpec tabSpec=multiDetailsTabs.newTabSpec(DETAILS_TAB_TAG_PREFIX +
				tabIndex);
		if (label==null) label=tabSpec.getTag();
		tabSpec.setIndicator(label);
		tabSpec.setContent(new TabHost.TabContentFactory() 
		{	
			@Override
			public View createTabContent(String tag) { return detailsView; }
		});
		return tabSpec;
	}
	
	public void removeDetailsTab(int tabIndex)
	{
		validateModeTabIndex(tabIndex);
		multiDetailsTabs.removeTab(tabIndex);
	}
}
