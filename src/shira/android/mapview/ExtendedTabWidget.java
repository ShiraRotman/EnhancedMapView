package shira.android.mapview;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TabWidget;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ExtendedTabWidget extends TabWidget
{
	public static final int DEFAULT_TAB_LABELS_HEIGHT=30;
	
	protected Field selectedTabField;
	protected Field selectionListenerField;
	protected Method selectionListenerMethod;
	
	protected int tabLabelsHeight;
	protected boolean autoAdjustHeight;
	
	protected class TabClickListener implements OnClickListener
	{
		private int tabIndex;
		public TabClickListener(int tabIndex) { this.tabIndex=tabIndex; }
		public int getTabIndex() { return tabIndex; }
		
		@Override public void onClick(View v) 
		{
			try
			{
				Object selectionListener=selectionListenerField.get(
						ExtendedTabWidget.this);
				selectionListenerMethod.invoke(selectionListener,tabIndex,true);
			}
			catch (IllegalAccessException accessException) { }
			catch (InvocationTargetException invocationException) 
			{ Log.i("ExtendedTabHost",invocationException.getMessage()); }
		}
	}
	
	public ExtendedTabWidget(Context context) 
	{ this(context,DEFAULT_TAB_LABELS_HEIGHT,true); }
	
	public ExtendedTabWidget(Context context,int tabLabelsHeight)
	{ this(context,tabLabelsHeight,false); }
	
	private ExtendedTabWidget(Context context,int tabLabelsHeight,boolean 
			adjustToDensity)
	{
		super(context);
		if (adjustToDensity)
		{
			float densityFactor=context.getResources().getDisplayMetrics().density;
			tabLabelsHeight=Math.round(tabLabelsHeight*densityFactor);
		}
		setTabLabelsHeight(tabLabelsHeight);
		initExtendedTabWidget();
	}
	
	public ExtendedTabWidget(Context context,AttributeSet attributeSet)
	{ this(context,attributeSet,0); }
	
	public ExtendedTabWidget(Context context,AttributeSet attributeSet,
			int defStyle)
	{ 
		super(context,attributeSet,defStyle);
		TypedArray attributesArray=context.obtainStyledAttributes(attributeSet,
				R.styleable.ExtendedTabWidget,defStyle,defStyle);
		autoAdjustHeight=attributesArray.getBoolean(R.styleable.
				ExtendedTabWidget_autoAdjustHeight,true);
		float densityFactor=context.getResources().getDisplayMetrics().density;
		int defaultTabLabelsHeight=Math.round(DEFAULT_TAB_LABELS_HEIGHT*
				densityFactor);
		setTabLabelsHeight(attributesArray.getDimensionPixelSize(R.styleable.
				ExtendedTabWidget_tabLabelsHeight,defaultTabLabelsHeight));
		attributesArray.recycle();
		initExtendedTabWidget(); 
	}
	
	protected void initExtendedTabWidget()
	{
		Class<?> tabWidgetClass=getClass().getSuperclass();
		try 
		{
			selectedTabField=tabWidgetClass.getDeclaredField("mSelectedTab");
			selectionListenerField=tabWidgetClass.getDeclaredField(
					"mSelectionChangedListener");
			Class<?> selectionInterface=Class.forName("android.widget." + 
					"TabWidget$OnTabSelectionChanged");
			selectionListenerMethod=selectionInterface.getDeclaredMethod(
					"onTabSelectionChanged",int.class,boolean.class);
		}
		catch (Exception exception) { }
		/*if (!selectionListenerField.isAccessible())
			selectionListenerField.setAccessible(true);*/
		AccessibleObject[] tabWidgetMembers={selectionListenerField,
				selectionListenerMethod,selectedTabField};
		AccessibleObject.setAccessible(tabWidgetMembers,true);
	}
	
	public boolean getAutoAdjustHeight() { return autoAdjustHeight; }
	
	public void setAutoAdjustHeight(boolean autoAdjustHeight)
	{
		this.autoAdjustHeight=autoAdjustHeight;
		if (autoAdjustHeight) updateTabLabelsHeight();
	}
	
	public int getTabLabelsHeight() 
	{
		if (!autoAdjustHeight)
			throw new IllegalStateException("The height of the tab labels " +
					"is variable! Set autoAdjustHeight to true to make the " +
					"height constant for all tab labels!");
		return tabLabelsHeight;
	}
	
	public void setTabLabelsHeight(int height)
	{
		if (height<0)
			throw new IllegalArgumentException("The tab labels height must " +
					"be at least 0!");
		autoAdjustHeight=true;
		tabLabelsHeight=height;
		updateTabLabelsHeight();
	}
	
	protected void updateTabLabelsHeight()
	{
		int childCount=getChildCount();
		for (int index=0;index<childCount;index++)
			getChildAt(index).getLayoutParams().height=tabLabelsHeight;
		requestLayout();
	}
	
	@Override public void addView(View child)
	{
		super.addView(child);
		child.setOnClickListener(new TabClickListener(getChildCount()-1));
		if (autoAdjustHeight)
		{
			child.getLayoutParams().height=tabLabelsHeight;
			requestLayout();
		}
	}
	
	@Override public void addView(View child,int index)
	{
		Log.i("ExtendedTabWidget","Index: " + index);
		super.addView(child,index);
		if (index>-1) updateIndexes(index);
		if (autoAdjustHeight)
		{
			child.getLayoutParams().height=tabLabelsHeight;
			requestLayout();
		}
	}
	
	@Override public void removeViewAt(int index)
	{
		super.removeViewAt(index);
		updateIndexes(index);
	}
	
	private void updateIndexes(int startIndex)
	{
		Log.i("ExtendedTabWidget","Start Index: " + startIndex);
		int childCount=getChildCount();
		for (int childIndex=startIndex;childIndex<childCount;childIndex++)
		{
			getChildAt(childIndex).setOnClickListener(new TabClickListener(
					childIndex));
		}
	}
	
	public void changeCurrentTab(int index)
	{ 
		try { selectedTabField.setInt(this,index); }
		catch (IllegalAccessException accessException) { }
	}
}
