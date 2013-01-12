package shira.android.mapview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;

public class MapRotationController 
{
	public static final long DEF_ROTATION_DURATION_MILLIS=300;
	
	private EnhancedMapView mapView;
	private RotationView rotationView;
	private ObjectAnimator rotationAnimator;
	private RotationAnimatorListener animatorListener;
	private float oldRotationDegrees;
	
	public static interface RotationAnimationListener
	{
		public abstract void onRotationEnd(MapRotationController rotationController,
				boolean canceled);
	}
	
	private class RotationAnimatorListener extends AnimatorListenerAdapter
	{
		private RotationAnimationListener animationListener;
		private boolean canceled;
		
		public RotationAnimatorListener(RotationAnimationListener listener)
		{ animationListener=listener; }
		
		/*public RotationAnimationListener getAnimationListener() 
		{ return animationListener; }*/
		
		public void setAnimationListener(RotationAnimationListener listener)
		{ animationListener=listener; }
		
		@Override public void onAnimationCancel(Animator animator) 
		{ canceled=true; }
		
		@Override public void onAnimationEnd(Animator animator)
		{ 
			if (animationListener!=null)
			{
				animationListener.onRotationEnd(MapRotationController.this,
						canceled);
			}
			notifyRotationListeners();
			oldRotationDegrees=rotationView.getRotationDegrees();
			canceled=false;
		}
	}
	
	MapRotationController(EnhancedMapView mapView)
	{
		if (mapView==null)
			throw new NullPointerException("The map view to operate on must " + 
					"be non-null!");
		ViewParent parent=mapView.getParent();
		if (!(parent instanceof RotationView))
			throw new IllegalArgumentException("The supplied map view does " +
					"not support rotation! In order to support it, the map " +
					"view must be contained inside a RotationView.");
		this.mapView=mapView;
		rotationView=(RotationView)parent;
		oldRotationDegrees=rotationView.getRotationDegrees();
	}
	
	public float getMapRotation() { return rotationView.getRotationDegrees(); }
	
	public void setMapRotation(float rotationDegrees)
	{
		if ((rotationAnimator!=null)&&(rotationAnimator.isRunning()))
			rotationAnimator.cancel();
		rotationDegrees%=360;
		if (rotationDegrees<0) rotationDegrees+=360;
		rotationView.setRotationDegrees(rotationDegrees);
		if (rotationDegrees!=oldRotationDegrees)
		{
			notifyRotationListeners();
			oldRotationDegrees=rotationDegrees;
		}
	}
	
	private void notifyRotationListeners()
	{
		for (MapViewChangeListener listener:mapView.getChangeListeners())
		{
			listener.onRotate(mapView,oldRotationDegrees,rotationView.
					getRotationDegrees());
		}
	}
	
	public void animateRotation(float rotationDegrees)
	{ animateRotation(rotationDegrees,true); }
	
	public void animateRotation(float rotationDegrees,boolean counterClockwise)
	{ animateRotation(rotationDegrees, counterClockwise, null); }
	
	public void animateRotation(float rotationDegrees,boolean counterClockwise,
			RotationAnimationListener animationListener)
	{ 
		animateRotation(rotationDegrees,counterClockwise,animationListener,
				DEF_ROTATION_DURATION_MILLIS);				
	}
	
	public void animateRotation(float rotationDegrees,boolean counterClockwise,
			RotationAnimationListener animationListener,long duration)
	{
		animateRotation(rotationDegrees,counterClockwise,animationListener,
				duration,false);
	}
	
	public void animateRotation(float rotationDegrees,boolean counterClockwise,
			RotationAnimationListener animationListener,long duration,
			boolean cancelRunningAnimation)
	{
		rotationDegrees%=360;
		if (rotationDegrees<0) rotationDegrees+=360;
		boolean negativeRotationDiff=(rotationDegrees<rotationView.
				getRotationDegrees());
		/*Important: Since the y-axis on computer screens goes downward, 
		 *contrary to the standard 2D coordinate system, the rotation actually 
		 *has to be clockwise to get to a bigger angle, and vice versa.*/
		if ((counterClockwise)&&(!negativeRotationDiff)) rotationDegrees-=360;
		else if ((!counterClockwise)&&(negativeRotationDiff)) rotationDegrees+=360;
		if (rotationAnimator==null)
		{
			rotationAnimator=new ObjectAnimator();
			rotationAnimator.setTarget(rotationView);
			rotationAnimator.setPropertyName("rotationDegrees");
			rotationAnimator.setInterpolator(new LinearInterpolator());
			//Store default duration since there's no constant for it...
			//defaultDuration=rotationAnimator.getDuration();
			animatorListener=new RotationAnimatorListener(animationListener);
			rotationAnimator.addListener(animatorListener);
			rotationAnimator.addUpdateListener(new ValueAnimator.
					AnimatorUpdateListener() 
			{
				@Override public void onAnimationUpdate(ValueAnimator animator) 
				{ Log.i("MapView","Animation: " + animator.getAnimatedValue()); }
			});
		}
		else
		{
			stopAnimation(!cancelRunningAnimation);
			animatorListener.setAnimationListener(animationListener);
		}
		rotationAnimator.setFloatValues(rotationDegrees);
		rotationAnimator.setDuration(duration);
		rotationAnimator.start();
	}
	
	public void stopAnimation(boolean jumpToFinish)
	{
		if (rotationAnimator.isRunning())
		{
			if (jumpToFinish) rotationAnimator.end();
			else rotationAnimator.cancel();
		}
	}
}
