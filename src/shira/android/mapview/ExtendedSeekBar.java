package shira.android.mapview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.SeekBar;

public class ExtendedSeekBar extends SeekBar
{
	private static final String SUPER_STATE_KEY_NAME="shira.android.SUPER_STATE";
	private static final String ORIENTATION_KEY_NAME="shira.android.mapview." +
			"SEEK_BAR_ORIENTATION";
	/*private static final String CHANGE_LISTENER_KEY_NAME="shira.android." + 
			"mapview.SEEK_BAR_CHANGE_LISTENER";*/
	
	private OnSeekBarChangeListener listener;
	private Orientation orientation;
	public enum Orientation { HORIZONTAL,VERTICAL };
	
	public ExtendedSeekBar(Context context) 
	{ this(context,Orientation.HORIZONTAL); }
	
	public ExtendedSeekBar(Context context,Orientation orientation)
	{
		super(context);
		this.orientation=orientation;
	}
	
	public ExtendedSeekBar(Context context,AttributeSet attributes)
	{ this(context,attributes,0); }
	
	public ExtendedSeekBar(Context context,AttributeSet attributes,int defStyle)
	{
		super(context,attributes,defStyle);
		TypedArray attributesArray=context.obtainStyledAttributes(attributes,
				R.styleable.ExtendedSeekBar,defStyle,defStyle);
		int orientationIndex;
		try
		{
			orientationIndex=attributesArray.getInt(R.styleable.
					ExtendedSeekBar_orientation,Orientation.HORIZONTAL.
					ordinal());
		}
		catch (Exception exception)
		{
			exception.printStackTrace();
			return;
		}
		finally { attributesArray.recycle(); }
		Log.i("MapView","Orientation: " + orientationIndex);
		orientation=Orientation.values()[orientationIndex];
		Log.i("MapView","Orientation: " + orientation.name());
	}
	
	public Orientation getOrientation() { return orientation; }
	public void setOrientation(Orientation orientation)
	{
		this.orientation=orientation;
		invalidate();
	}
	
	@Override 
	public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener)
	{
		super.setOnSeekBarChangeListener(listener);
		this.listener=listener;
	}
	
	@Override public Parcelable onSaveInstanceState()
	{
		Bundle bundle=new Bundle();
		bundle.putParcelable(SUPER_STATE_KEY_NAME,super.onSaveInstanceState());
		bundle.putInt(ORIENTATION_KEY_NAME,orientation.ordinal());
		return bundle;
	}
	
	@Override public void onRestoreInstanceState(Parcelable state)
	{
		if (state instanceof Bundle)
		{
			Bundle bundle=(Bundle)state;
			super.onRestoreInstanceState(bundle.getParcelable(SUPER_STATE_KEY_NAME));
			int orientationIndex=bundle.getInt(ORIENTATION_KEY_NAME,Orientation.
					HORIZONTAL.ordinal());
			setOrientation(Orientation.values()[orientationIndex]);
		}
		else super.onRestoreInstanceState(state);
	}
	
	@Override
    protected synchronized void onMeasure(int widthMeasureSpec,int 
    		heightMeasureSpec) 
	{
		if (orientation==Orientation.HORIZONTAL)
			super.onMeasure(widthMeasureSpec,heightMeasureSpec);
		else
		{
			super.onMeasure(heightMeasureSpec,widthMeasureSpec);
			setMeasuredDimension(getMeasuredHeight(),getMeasuredWidth());
		}
    }
	
	@Override protected void onSizeChanged(int w,int h,int oldw,int oldh) 
	{
		if (orientation==Orientation.HORIZONTAL)
			super.onSizeChanged(w,h,oldw,oldh);
		else super.onSizeChanged(h,w,oldh,oldw);
	}
	
	@Override protected synchronized void onDraw(Canvas canvas) 
	{
		if (orientation==Orientation.VERTICAL)
		{
			canvas.rotate(-90);
			canvas.translate(-getHeight(),0);
		}
        super.onDraw(canvas);
    }
	
	@Override public synchronized void setProgress(int progress)
	{
		super.setProgress(progress);
		if (orientation==Orientation.VERTICAL) 
			onSizeChanged(getWidth(),getHeight(),0,0);
	}
	
	@Override public boolean onTouchEvent(MotionEvent event)
	{
		if (orientation==Orientation.HORIZONTAL) 
			return super.onTouchEvent(event);
		//else if ((!isEnabled()||(!isClickable())) return false;
		else if (!isEnabled()) return false;
		else
		{
			int actionMasked=event.getActionMasked();
			if (actionMasked!=MotionEvent.ACTION_CANCEL)
				setProgress(getMax()-(int)(getMax()*event.getY()/getHeight()));
			switch (actionMasked) 
			{
	            case MotionEvent.ACTION_DOWN:
	            	setPressed(true);
	            	if (listener!=null) listener.onStartTrackingTouch(this);
	            	break;
	            case MotionEvent.ACTION_MOVE:
	            	setPressed(true);
	            	if (listener!=null) 
	            		listener.onProgressChanged(this,getProgress(),true);
	            	break;
	            case MotionEvent.ACTION_UP:
	            case MotionEvent.ACTION_CANCEL:
	            	setPressed(false);
	            	if (listener!=null) listener.onStopTrackingTouch(this);
	            	break;
			}
			return true;
		}
	}
}
