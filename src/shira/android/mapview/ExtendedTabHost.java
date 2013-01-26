package shira.android.mapview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
//import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;

import java.lang.reflect.*;
import java.util.LinkedList;

public class ExtendedTabHost extends TabHost
{
	protected LinkedList<TabSpec> tabSpecs;
	//private Field tabSpecsField; //,currentViewField;
	protected Field currentTabField;
	//private Method addElementMethod,removeElementMethod;
	protected OnTabChangeListener tabChangeListener;
	protected boolean invokeListener=true;
	
	public ExtendedTabHost(Context context)
	{
		super(context);
		initExtendedTabHost();
	}
	
	public ExtendedTabHost(Context context,AttributeSet attributeSet)
	{
		super(context,attributeSet);
		initExtendedTabHost();
	}
	
	private void initExtendedTabHost()
	{
		Class<?> tabHostClass=getClass().getSuperclass();
		Field tabSpecsField=null;
		try 
		{ 
			tabSpecsField=tabHostClass.getDeclaredField("mTabSpecs");
			currentTabField=tabHostClass.getDeclaredField("mCurrentTab");
			//currentViewField=tabHostClass.getDeclaredField("mCurrentView");
		}
		/*Not supposed to occur since the fields exist (unless changed /
		 *removed in a future version of the superclass)*/
		catch (NoSuchFieldException fieldException) { }
		AccessibleObject[] tabHostFields={tabSpecsField,currentTabField};
		AccessibleObject.setAccessible(tabHostFields,true);
		//if (!tabSpecsField.isAccessible()) tabSpecsField.setAccessible(true);
		tabSpecs=new LinkedList<TabSpec>();
		try { tabSpecsField.set(this,tabSpecs); }
		//Not supposed to occur since the fields were made accessible
		catch (IllegalAccessException accessException) { }
		/*Class<LinkedList> linkedListClass=LinkedList.class;
		try
		{
			addElementMethod=linkedListClass.getMethod("add",int.class,
					Object.class);
			removeElementMethod=linkedListClass.getMethod("remove",int.class);
		}*/
		/*Not supposed to occur since the methods exist (and unlikely to be 
		 *changed / removed because they are public)*/
		//catch (NoSuchMethodException methodException) { }
		setOnTabChangedListener(new OnTabChangeListener() 
		{	
			@Override public void onTabChanged(String tabId) 
			{
				if ((tabChangeListener!=null)&&(invokeListener))
					tabChangeListener.onTabChanged(tabId);
			}
		});
	}
	
	public void setTabLabelsHeight(int height)
	{ ((ExtendedTabWidget)getTabWidget()).setTabLabelsHeight(height); }
	
	@Override public void setOnTabChangedListener(OnTabChangeListener listener)
	{ tabChangeListener=listener; }
	
	public void validateTabIndex(int tabIndex)
	{ validateTabIndex(tabIndex,false); }
	
	private void validateTabIndex(int tabIndex,boolean includeChildCount)
	{
		int maxTabNum=getTabWidget().getChildCount();
		if (!includeChildCount) maxTabNum--;
		/*if (maxTabNum==-1)
			throw new IllegalArgumentException("There are no detail tabs " +
					"right now, so the tab index is illegal!");*/ 
		if ((tabIndex<0)||(tabIndex>maxTabNum))
			throw new ArrayIndexOutOfBoundsException("The tab index must be " + 
					"between 0 and " + maxTabNum + "! The value supplied was " +
					tabIndex);
	}
	
	@Override public void addTab(TabSpec tabSpec)
	{
		super.addTab(tabSpec);
		invokeListener=false;
		int currentTabIndex=getCurrentTab();
		/*When a new tab is added to the tab host, it's not automatically added 
		 *to the actual tab container, but only when this tab becomes the 
		 *current one for the first time. This lazy initialization is good and 
		 *efficient, however in the extended view I want to support working with 
		 *the tabs and changing their properties from the moment they are added, 
		 *and therefore I must make every new tab the current one and 
		 *immediately cancel the operation just for it to be added to the 
		 *container.*/
		setCurrentTab(getTabWidget().getChildCount()-1);
		setCurrentTab(currentTabIndex);
		invokeListener=true;
	}
	
	public void addTab(TabSpec tabSpec,int tabIndex)
	{
		validateTabIndex(tabIndex,true);
		Log.i("ExtendedTabHost","Tab Index: " + tabIndex);
		addTab(tabSpec);
		TabWidget tabWidget=getTabWidget();
		int maxChildIndex=tabWidget.getChildCount()-1;
		if (maxChildIndex>0)
		{
			tabSpecs.remove(maxChildIndex);
			tabSpecs.add(tabIndex,tabSpec);
			View tabIndicator=tabWidget.getChildAt(maxChildIndex);
			//Log.i("ExtendedTabHost","Max Index: " + maxChildIndex);
			tabWidget.removeViewAt(maxChildIndex);
			tabWidget.addView(tabIndicator,tabIndex);
			try
			{
				int currentTabIndex=currentTabField.getInt(this);
				if (currentTabIndex>=tabIndex)
				{
					currentTabIndex++;
					currentTabField.setInt(this,currentTabIndex);
					tabWidget.setCurrentTab(currentTabIndex);
				}
			}
			//Not supposed to occur since the field was made accessible
			catch (IllegalAccessException accessException) { }
		}
	}
	
	public void removeTab(int tabIndex)
	{
		validateTabIndex(tabIndex);
		TabWidget tabWidget=getTabWidget();
		int currentTabIndex=0,updatedTabIndex;
		try { currentTabIndex=currentTabField.getInt(this); }
		catch (IllegalAccessException accessException) { }
		if (currentTabIndex==tabIndex)
		{
			int selectedTabIndex;
			if (currentTabIndex==tabWidget.getChildCount()-1) 
			{
				selectedTabIndex=currentTabIndex-1;
				updatedTabIndex=-1;
			}
			else
			{
				selectedTabIndex=currentTabIndex+1;
				updatedTabIndex=currentTabIndex;
			}
			Log.i("ExtendedTabHost","Index1: " + selectedTabIndex);
			setCurrentTab(selectedTabIndex);
			tabWidget.setCurrentTab(selectedTabIndex);
		}
		else updatedTabIndex=currentTabIndex-1;
		tabWidget.removeViewAt(tabIndex);
		tabSpecs.remove(tabIndex);
		/*TODO: I also have to remove the view of the tab content itself from 
		 *the tabs content container, but because it requires more dealings with 
		 *reflection, I leave it as it is for now since it doesn't cause issues 
		 *with the control's behavior. It might, however, cause memory leakage 
		 *in very dynamic instances where tabs are added and removed all the 
		 *time. For this reason, it's recommended to clean and rebuild the tabs 
		 *every once in a certain number of removals. Future versions may 
		 *address this issue and perform automatic cleaning, or handle the 
		 *removal from the content container.*/
		if ((currentTabIndex>=tabIndex)&&(updatedTabIndex>-1))
		{
			//currentTabIndex--;
			Log.i("ExtendedTabHost","Index2: " + currentTabIndex);
			try { currentTabField.setInt(this,updatedTabIndex); }
			catch (IllegalAccessException accessException) { }
			((ExtendedTabWidget)tabWidget).changeCurrentTab(updatedTabIndex);
		}
	}
}
