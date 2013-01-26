package shira.android.mapview;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;

public class TestCustomViewsActivity extends Activity 
{
	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.test_seek_bar);
		//setContentView(new ExtendedSeekBar(this));
		testExtendedTabHost();
	}
	
	private void testExtendedTabHost()
	{
		setContentView(R.layout.geo_point_details_multi);
		ExtendedTabHost extendedTabHost=(ExtendedTabHost)findViewById(R.id.
				extended_tab_host);
		extendedTabHost.setup();
		TabHost.TabContentFactory tabContentFactory=new TabHost.TabContentFactory() 
		{
			@Override public View createTabContent(String tag) 
			{
				TextView tabContent=new TextView(TestCustomViewsActivity.this);
				tabContent.setText(tag);
				return tabContent;
			}
		};
		TabHost.TabSpec tabSpec=extendedTabHost.newTabSpec("Test1");
		tabSpec.setIndicator("Test1");
		tabSpec.setContent(tabContentFactory);
		extendedTabHost.addTab(tabSpec);
		tabSpec=extendedTabHost.newTabSpec("Test2");
		tabSpec.setIndicator("Test2");
		tabSpec.setContent(tabContentFactory);
		extendedTabHost.addTab(tabSpec);
		tabSpec=extendedTabHost.newTabSpec("Test3");
		tabSpec.setIndicator("Test3");
		tabSpec.setContent(tabContentFactory);
		extendedTabHost.addTab(tabSpec,0);
		//extendedTabHost.removeTab(0);
		extendedTabHost.setTabLabelsHeight(40);
	}
}
