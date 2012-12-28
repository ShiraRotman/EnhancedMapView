package shira.android.mapview;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class RotationView extends ViewGroup
{	
	private float rotationDegrees=0;
	private boolean rotateMotionEvents;
	private Matrix rotationMatrix=new Matrix();
	private static PaintFlagsDrawFilter drawFilter;
	
	static { drawFilter=new PaintFlagsDrawFilter(0,Paint.FILTER_BITMAP_FLAG); }
	
	public RotationView(Context context) { this(context,false); }
	public RotationView(Context context,boolean rotateMotionEvents)
	{ super(context); rotateMotionEvents(rotateMotionEvents); }
	
	public float getRotationDegrees() { return rotationDegrees; }
	public void setRotationDegrees(float rotationDegrees)
	{ 
		this.rotationDegrees=rotationDegrees%360;
		if (this.rotationDegrees<0) this.rotationDegrees+=360;
		Log.i("MapView","View degrees: " + this.rotationDegrees);
		invalidate();
	}
	
	public boolean rotateMotionEvents() { return rotateMotionEvents; }
	public void rotateMotionEvents(boolean rotateMotionEvents)
	{ this.rotateMotionEvents=rotateMotionEvents; }
	
	@Override protected void dispatchDraw(Canvas canvas)
	{
		Log.i("MapView","Redrawing");
		int saveCount=canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.rotate(rotationDegrees,getWidth()/2,getHeight()/2);
		canvas.setDrawFilter(drawFilter);
		canvas.getMatrix(rotationMatrix);
		super.dispatchDraw(canvas);
		canvas.restoreToCount(saveCount);
	}
	
	@Override public boolean dispatchTouchEvent(MotionEvent event)
	{
		if (rotateMotionEvents) event.transform(rotationMatrix);
		return super.dispatchTouchEvent(event);
	}
	
	@Override public boolean dispatchTrackballEvent(MotionEvent event)
	{
		if (rotateMotionEvents) event.transform(rotationMatrix);
		return super.dispatchTrackballEvent(event);
	}
	
	@Override protected void onLayout(boolean changed,int l,int t,int r,int b) 
	{
        final int width=getWidth();
        final int height=getHeight();
        final int count=getChildCount();
        for (int i=0;i<count;i++) 
        {
            final View view=getChildAt(i);
            final int childWidth=view.getMeasuredWidth();
            final int childHeight=view.getMeasuredHeight();
            final int childLeft=(width-childWidth)/2;
            final int childTop=(height-childHeight)/2;
            Log.i("MapView","Map: " + childLeft + "," + childTop);
            /*view.setTag(R.id.view_left_coord,childLeft); 
            view.setTag(R.id.view_top_coord,childTop);*/
            view.layout(childLeft,childTop,childLeft+childWidth,childTop+
            		childHeight);
        }
    }
	
	@Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec) 
	{
		Log.i("MapView","Measure");
        int w=getDefaultSize(getSuggestedMinimumWidth(),widthMeasureSpec);
        int h=getDefaultSize(getSuggestedMinimumHeight(),heightMeasureSpec);
        int sizeSpec;
        if (w>h)
        {
            sizeSpec=MeasureSpec.makeMeasureSpec((int)(w*Math.sqrt(2)),
            		MeasureSpec.EXACTLY);
        }
        else 
        {
            sizeSpec=MeasureSpec.makeMeasureSpec((int)(h*Math.sqrt(2)),
            		MeasureSpec.EXACTLY);
        }
        final int count=getChildCount();
        for (int i=0;i<count;i++) getChildAt(i).measure(sizeSpec,sizeSpec);
        super.onMeasure(widthMeasureSpec,heightMeasureSpec);
    }
}
