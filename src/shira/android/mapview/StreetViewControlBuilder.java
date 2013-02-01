package shira.android.mapview;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.view.*;
//import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.maps.GeoPoint;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.*;
import java.util.Map;

class StreetViewControlBuilder implements MapControlBuilder 
{
	public static final String IMAGE_NOT_INITIALIZED="The control cannot be " +
			"measured before it's created! Call buildControl to create the " + 
			"control.";
	private Bitmap streetViewIcon;
	
	private static interface StreetViewOperationListener
	{
		public abstract void streetViewFinished();
	}
	
	private static class StreetViewRetriever extends AsyncTask<Number,Void,Bitmap>
	{
		private Context context;
		private Dialog communicationProgressDialog;
		private StreetViewOperationListener streetViewListener;
		private Exception streetViewException;
		//private boolean isLargeScreen;
		
		//public StreetViewRetriever(Context context) { this(context,null); }
		public StreetViewRetriever(Context context,StreetViewOperationListener 
				listener)
		{
			this.context=context;
			streetViewListener=listener; 
			/*isLargeScreen=context.getResources().getBoolean(R.bool.
					is_large_screen);*/
		}
		
		@Override protected void onPreExecute()
		{
			/*communicationProgressDialog=new ProgressDialog(context,R.style.
					ProgressDialog);
			communicationProgressDialog.setIndeterminate(true);*/
			communicationProgressDialog=new Dialog(context,R.style.ProgressDialog);
			communicationProgressDialog.requestWindowFeature(Window.
					FEATURE_NO_TITLE);
			ProgressBar progressBar=new ProgressBar(context);
			progressBar.setIndeterminate(true);
			communicationProgressDialog.setContentView(progressBar);
			communicationProgressDialog.setOnDismissListener(new DialogInterface.
					OnDismissListener() 
			{	
				@Override public void onDismiss(DialogInterface dialog) 
				{ cancel(true); }
			});
			communicationProgressDialog.show();
		}
		
		@Override protected Bitmap doInBackground(Number... params)
		{
			String streetViewAddress="http://maps.googleapis.com/maps/api/" + 
					"streetview?" + buildQueryString(params);
			Log.i("MapView","Address: " + streetViewAddress);
			HttpURLConnection httpConnection=null;
			BufferedInputStream responseStream=null;
			try
			{
				URL url=new URL(streetViewAddress);
				URLConnection connection=url.openConnection();
				if (!(connection instanceof HttpURLConnection))
				{
					throw new IOException("Could not open connection " +
							"to Google Street View service!");
				}
				httpConnection=(HttpURLConnection)connection;
				int responseCode=httpConnection.getResponseCode();
				if (responseCode==HttpURLConnection.HTTP_FORBIDDEN)
				{
					throw new IOException("The Google Street View service " +
							"is forbidden for access! Request quota might " +
							"have exceeeded!");
				}
				else if (responseCode!=HttpURLConnection.HTTP_OK)
				{
					throw new IOException("The Google Street View service " +
							"returned an error response of " + responseCode +
							": " + httpConnection.getResponseMessage() + "!");
				}
				else
				{
					responseStream=new BufferedInputStream(httpConnection.
							getInputStream());
					Bitmap streetViewBitmap=BitmapFactory.decodeStream(
							responseStream);
					return streetViewBitmap;
				}
			}
			catch (IOException ioException)
			{
				streetViewException=ioException;
				return null;
			}
			/*According to the documentation, if a thread is interrupted while 
			 *in a try/catch block, the finally block may not execute, and in 
			 *that case there will be a memory leak because resources won't get 
			 *closed and released.*/
			finally
			{
				if (responseStream!=null)
				{
					try { responseStream.close(); }
					catch (IOException ioException) { }
				}
				if (httpConnection!=null) httpConnection.disconnect();
			}
		}
		
		private String buildQueryString(Number... params)
		{
			StringBuilder streetViewQueryString=new StringBuilder();
			streetViewQueryString.append("key=");
			streetViewQueryString.append(context.getResources().getString(
					R.string.google_services_api_key));
			streetViewQueryString.append("&location=");
			streetViewQueryString.append(params[0]);
			streetViewQueryString.append(",");
			streetViewQueryString.append(params[1]);
			streetViewQueryString.append("&size=");
			streetViewQueryString.append(params[2]);
			streetViewQueryString.append("x");
			streetViewQueryString.append(params[3]);
			if (params.length>=5)
			{
				streetViewQueryString.append("&heading=");
				streetViewQueryString.append(params[4]);
				if (params.length>=6)
				{
					streetViewQueryString.append("&fov=");
					streetViewQueryString.append(params[5]);
					if (params.length>=7)
					{
						streetViewQueryString.append("&pitch=");
						streetViewQueryString.append(params[6]);
					}
				}
			}
			streetViewQueryString.append("&sensor=false");
			return streetViewQueryString.toString();
		}
		
		@Override protected void onCancelled(Bitmap streetViewBitmap)
		{ 
			if (streetViewListener!=null) 
				streetViewListener.streetViewFinished(); 
		}
		
		@Override protected void onPostExecute(Bitmap streetViewBitmap)
		{
			communicationProgressDialog.setOnDismissListener(null);
			communicationProgressDialog.dismiss();
			if (streetViewBitmap==null)
			{
				String streetViewErrorMessage;
				if (streetViewException!=null)
					streetViewErrorMessage=streetViewException.getMessage();
				else
				{
					streetViewErrorMessage="Could not retrieve the image for " +
							"the position selected from the Google Street " + 
							"View service! Connection was established and " + 
							"response was received, but the image data could " +
							"not be decoded!";
				}
				new AlertDialog.Builder(context).setMessage(streetViewErrorMessage).
						setPositiveButton(android.R.string.ok,new DialogInterface.
						OnClickListener() 
				{			
					@Override 
					public void onClick(DialogInterface dialog,int which) 
					{ dialog.dismiss(); }
				})
				.create().show();						
			}
			else
			{
				ImageView streetViewImage=new ImageView(context);
				streetViewImage.setImageBitmap(streetViewBitmap);
				GeoPointDetailsView detailsView=new GeoPointDetailsView(context);
				//detailsView.setPadding(0,0,0,0);
				detailsView.setDetailsView(streetViewImage);
				AlertDialog dialog=new AlertDialog.Builder(context).create();
				dialog.setView(streetViewImage,0,0,0,0);
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
				{	
					@Override public void onDismiss(DialogInterface dialog) 
					{ 
						if (streetViewListener!=null)
							streetViewListener.streetViewFinished();
					}
				});
				dialog.show();
			}
		}
	}
	
	private static class StreetViewDragDropHandler implements View.OnTouchListener
	{
		private static final long MIN_DRAGGING_MILLIS=500;
		private static final int MAX_IMAGE_WIDTH=640;
		private static final int MAX_IMAGE_HEIGHT=640;
		
		private static final int MIN_ZOOM_LEVEL=1;
		private static final int MAX_ZOOM_LEVEL=21;
		private static final int MIN_FIELD_OF_VIEW=1;
		private static final int MAX_FIELD_OF_VIEW=120;
		private static final int ZOOM_FIELD_DEGREES=(MAX_FIELD_OF_VIEW-
				MIN_FIELD_OF_VIEW)/(MAX_ZOOM_LEVEL-MIN_ZOOM_LEVEL);
		
		private View control;
		private EnhancedMapView mapView;
		private StreetViewOperationListener streetViewListener;
		private float originalPositionX,originalPositionY;
		private long draggingMillis;
		//private int pointerID=-1;
		private boolean lastGestureFinished=true;
		
		public StreetViewDragDropHandler(View control,EnhancedMapView mapView)
		{
			this.control=control;
			this.mapView=mapView; 
		}
		
		public boolean onTouch(View view,MotionEvent event)
		{
			switch (event.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN:
					/*According to the documentation, an ACTION_DOWN event might 
					 *occur without an ACTION_UP/ACTION_CANCEL event that 
					 *finishes the previous gesture first, and the event handler 
					 *has to address such situations. In this case, a gesture 
					 *represents the dragging of the control, and when that 
					 *finishes, the control has to return to its original 
					 *position where the dragging started from. Therefore, the 
					 *ACTION_DOWN handler is expected to reposition the control 
					 *if the ACTION_UP/CANCEL event for the previous gesture 
					 *did not occur.*/
					if (!lastGestureFinished)
					{
						control.setTranslationX(0);
						control.setTranslationY(0);
					}
					//pointerID=event.getPointerId(0);
					originalPositionX=event.getRawX();
					originalPositionY=event.getRawY();
					lastGestureFinished=false;
					draggingMillis=SystemClock.uptimeMillis();
					break;
				case MotionEvent.ACTION_MOVE:
					/*int pointerIndex=event.findPointerIndex(pointerID);
					Log.i("MapView","Index: " + pointerIndex);
					if (pointerIndex>-1)
					{*/
					control.setTranslationX(event.getRawX()-originalPositionX);
					control.setTranslationY(event.getRawY()-originalPositionY);
					Log.i("MapView","Street View: " + event.getRawX() + "," + 
							event.getRawY());
					//}
					break;
				case MotionEvent.ACTION_UP:
					/*pointerIndex=event.findPointerIndex(pointerID);
					if (pointerIndex>-1)
					{*/
					if (SystemClock.uptimeMillis()-draggingMillis>=
							MIN_DRAGGING_MILLIS)
					{
						float positionX=event.getRawX();
						float positionY=event.getRawY();
						control.setTranslationX(positionX-originalPositionX);
						control.setTranslationY(positionY-originalPositionY);
						sendStreetViewRequest(positionX,positionY);
					}
					else finishDragging();
					/*}
					else finishDragging();*/
					break;
				case MotionEvent.ACTION_CANCEL:
					finishDragging();
					break;
			}
			return true;
		}
		
		private void sendStreetViewRequest(float positionX,float positionY)
		{
			if (streetViewListener==null)
			{
				streetViewListener=new StreetViewOperationListener() 
				{	
					@Override public void streetViewFinished() 
					{ finishDragging(); }
				};
			}
			StreetViewRetriever streetViewRetriever=new StreetViewRetriever(
					mapView.getContext(),streetViewListener);
			positionX-=mapView.getLeft(); positionY-=mapView.getTop();
			GeoPoint streetViewPoint=mapView.getProjection().fromPixels(Math.
					round(positionX),Math.round(positionY));
			float latitude=(float)(streetViewPoint.getLatitudeE6())/1000000; //10^6
			float longitude=(float)(streetViewPoint.getLongitudeE6())/1000000;
			Log.i("MapView","Geo: " + latitude + "," + longitude);
			Resources resources=mapView.getResources();
			int imageWidth=resources.getDimensionPixelSize(R.dimen.
					street_view_image_width);
			if (imageWidth>MAX_IMAGE_WIDTH) imageWidth=MAX_IMAGE_WIDTH;
			int imageHeight=resources.getDimensionPixelSize(R.dimen.
					street_view_image_height);
			if (imageHeight>MAX_IMAGE_HEIGHT) imageHeight=MAX_IMAGE_HEIGHT;
			int heading;
			if (mapView.supportsRotation())
			{
				heading=Math.round(mapView.getRotationController().
						getMapRotation());
			}
			else heading=0;
			int fieldOfView=MAX_FIELD_OF_VIEW-(mapView.getZoomLevel()-1)*
					ZOOM_FIELD_DEGREES;
			/*TODO: Think of a way to calculate the pitch (using sensors to 
			 *detect the device's direction and setting the pitch angle 
			 *accordingly is not very useful since it requires the user to 
			 *rotate the device which will make it hard to use the application*/
			int pitch=0;
			streetViewRetriever.execute(latitude,longitude,imageWidth,
					imageHeight,heading,fieldOfView,pitch);
		}
		
		private void finishDragging()
		{
			control.setTranslationX(0);
			control.setTranslationY(0);
			//pointerID=-1; 
			draggingMillis=0;
			lastGestureFinished=true;
		}
	}
	
	@Override
	public View buildControl(Map<String,Object> properties,View existingControl,
			EnhancedMapView mapView)
	{
		Context context=MapControlDefsUtils.extractContext(existingControl,mapView);
		if (streetViewIcon==null)
		{
			streetViewIcon=BitmapFactory.decodeResource(context.getResources(),
					R.drawable.street_view);
		}
		ImageView control;
		if (existingControl instanceof ImageView) 
			control=(ImageView)existingControl;
		else control=new ImageView(context);
		control.setImageBitmap(streetViewIcon);
		return control;
	}
	
	@Override public void registerListeners(EnhancedMapView mapView,View control)
	{
		if ((control instanceof ImageView)&&(control.getTag(R.id.
				control_listener)==null))
		{
			StreetViewDragDropHandler streetViewListener=new StreetViewDragDropHandler(
					control,mapView);
			control.setOnTouchListener(streetViewListener);
			control.setTag(R.id.control_listener,streetViewListener);
		}
	}
	
	@Override public int getMinimumWidth(Map<String,Object> properties)
	{
		if (streetViewIcon==null)
			throw new IllegalStateException(IMAGE_NOT_INITIALIZED);
		return streetViewIcon.getWidth();
	}
	
	@Override public int getMinimumHeight(Map<String,Object> properties)
	{
		if (streetViewIcon==null)
			throw new IllegalStateException(IMAGE_NOT_INITIALIZED);
		return streetViewIcon.getHeight();
	}
	
	@Override public EnhancedMapView.ControlAlignment getDefaultAlignment()
	{ return EnhancedMapView.ControlAlignment.LEFT_TOP; }
}
