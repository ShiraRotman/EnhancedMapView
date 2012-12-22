package shira.android.mapview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import java.util.*;

class ViewsActivationManager 
{
	private static final long UNTOUCHED_TIMEOUT=5000;
	//private static final long ANIMATION_DURATION=300;
	private static final float MIN_OPACITY_VALUE=0.4f;
	private static final float MAX_OPACITY_VALUE=1;
	
	private static final int INITIAL_NUM_ELEMENTS=3;
	private static ViewsActivationManager instance=null;
	
	private Map<View,ViewActivationData> viewsActivationMap;
	/*The object pools don't have to be thread-safe since only the UI thread 
	 *uses them*/
	private List<UntouchedViewTimeoutCallback> untouchedCallbacksPool;
	private List<ObjectAnimator> activationAnimatorsPool;
	private ObjectAnimator activationAnimatorPrototype;
	private ActivationViewListener activationViewListener;
	private ActivationAnimatorListener animatorListener;
	
	private enum CallbackState { PENDING,STOPPED,RUNNING,FINISHED };
	
	private static class ViewActivationData
	{
		public UntouchedViewTimeoutCallback untouchedTimeoutCallback;
		public ObjectAnimator activationAnimator;
	}
	
	private class UntouchedViewTimeoutCallback implements Runnable
	{
		private View untouchedView;
		private CallbackState state=CallbackState.PENDING;
		
		public UntouchedViewTimeoutCallback() { }
		
		//public View getUntouchedView() { return untouchedView; }
		public void setUntouchedView(View untouchedView) 
		{ this.untouchedView=untouchedView; }
		
		public CallbackState getState() { return state; }
		public void reset() { state=CallbackState.PENDING; }
		public void stop() 
		{ if (state!=CallbackState.FINISHED) state=CallbackState.STOPPED; }
		
		@Override public void run()
		{
			if (state==CallbackState.STOPPED) untouchedCallbacksPool.add(this);
			else
			{
				Log.i("MapView","Inactivating: " + this);
				state=CallbackState.RUNNING;
				ViewActivationData  viewActivationData=viewsActivationMap.get(
						untouchedView);
				ObjectAnimator activationAnimator=getActivationAnimator();
				viewActivationData.activationAnimator=activationAnimator;
				activationAnimator.setTarget(untouchedView);
				activationAnimator.setFloatValues(MIN_OPACITY_VALUE);
				activationAnimator.start();
				state=CallbackState.FINISHED;
			}
		}
	}
	
	private class ActivationViewListener implements View.OnTouchListener
	{
		@Override public boolean onTouch(View view,MotionEvent event)
		{
			boolean consumed;
			if (event.getActionMasked()==MotionEvent.ACTION_DOWN)
			{
				Log.i("MapView","Touched");
				//view.setOnTouchListener(null);
				View rootView=view; //boolean rootTouched=true;
				ViewActivationData viewActivationData=viewsActivationMap.
						get(view);
				while (viewActivationData==null)
				{
					rootView=(View)rootView.getParent();
					viewActivationData=viewsActivationMap.get(rootView);
				}
				UntouchedViewTimeoutCallback untouchedCallback=viewActivationData.
						untouchedTimeoutCallback;
				consumed=(untouchedCallback==null);
				view.setTag(R.id.touch_consumed_indication,consumed);
				if (viewActivationData.activationAnimator!=null)
					viewActivationData.activationAnimator.cancel();
				else if (untouchedCallback!=null)
				{
					Log.i("MapView","Removing: " + untouchedCallback);
					untouchedCallback.stop();
					Handler handler=new Handler(Looper.getMainLooper());
					handler.removeCallbacks(untouchedCallback); //Not working!!!
					releaseActivationResources(viewActivationData);
					handleActiveView(rootView,viewActivationData);
				}
				else activateView(rootView,viewActivationData);
			} //end if
			else 
			{
				consumed=((Boolean)view.getTag(R.id.touch_consumed_indication)).
						booleanValue();
			}
			return consumed;
		}
	}
	
	private class ActivationAnimatorListener extends AnimatorListenerAdapter
	{
		public void onAnimationCancel(Animator animation)
		{
			ObjectAnimator activationAnimator=(ObjectAnimator)animation;
			View view=(View)activationAnimator.getTarget();
			view.setTag(R.id.was_canceled_indication,true);
		}
		
		public void onAnimationEnd(Animator animation)
		{
			Log.i("MapView","Ended");
			ObjectAnimator activationAnimator=(ObjectAnimator)animation;
			View view=(View)activationAnimator.getTarget();
			ViewActivationData viewActivationData=viewsActivationMap.get(view);
			UntouchedViewTimeoutCallback untouchedCallback=viewActivationData.
					untouchedTimeoutCallback;
			releaseActivationResources(viewActivationData);
			boolean removeView;
			Object removeViewWrapper=view.getTag(R.id.remove_view_indication);
			if (removeViewWrapper!=null)
			{
				removeView=((Boolean)removeViewWrapper).booleanValue();
				view.setTag(R.id.remove_view_indication,null);
			}
			else removeView=false;
			if (removeView) viewsActivationMap.remove(view);
			else if (untouchedCallback!=null)
			{
				boolean animationCanceled;
				Object animationCanceledWrapper=view.getTag(R.id.
						was_canceled_indication);
				if (animationCanceledWrapper!=null)
				{
					animationCanceled=((Boolean)animationCanceledWrapper).
							booleanValue();
					view.setTag(R.id.was_canceled_indication,null);
				}
				else animationCanceled=false;
				if (animationCanceled)
				{
					releaseActivationResources(viewActivationData);
					activateView(view,viewActivationData);
				}
				/*else
				{
					Log.i("MapView","Disabling");
					//view.setEnabled(false);
					if (view instanceof ViewGroup)
						changeViewGroupResponsiveness((ViewGroup)view,false);					
					//view.setOnTouchListener(activationViewListener);
				}*/
			} //end else if
			else
			{
				Log.i("MapView","Enabled");
				//view.setEnabled(true);
				/*if (view instanceof ViewGroup)
					changeViewGroupResponsiveness((ViewGroup)view,true);*/
				handleActiveView(view,viewActivationData);
			}
		}
	}
	
	private ViewsActivationManager()
	{
		viewsActivationMap=new HashMap<View,ViewActivationData>();
		untouchedCallbacksPool=new LinkedList<UntouchedViewTimeoutCallback>();
		activationAnimatorsPool=new LinkedList<ObjectAnimator>();
		activationViewListener=new ActivationViewListener();
		animatorListener=new ActivationAnimatorListener();
		activationAnimatorPrototype=createActivationAnimator();
		activationAnimatorsPool.add(activationAnimatorPrototype);
		for (int counter=0;counter<INITIAL_NUM_ELEMENTS;counter++)
			untouchedCallbacksPool.add(new UntouchedViewTimeoutCallback());
		for (int counter=1;counter<INITIAL_NUM_ELEMENTS;counter++)
			activationAnimatorsPool.add(activationAnimatorPrototype.clone());
	}
	
	private ObjectAnimator createActivationAnimator()
	{
		ObjectAnimator activationAnimator=new ObjectAnimator();
		activationAnimator.setPropertyName("alpha");
		activationAnimator.setInterpolator(new LinearInterpolator());
		//activationAnimator.setDuration(ANIMATION_DURATION);
		activationAnimator.addListener(animatorListener);
		return activationAnimator;
	}
	
	public static ViewsActivationManager getInstance()
	{
		if (instance==null) instance=new ViewsActivationManager();
		return instance;
	}
	
	public void registerView(View view)
	{
		if (view==null)
			throw new NullPointerException("The view to register for " + 
					"activation management must be non-null!");
		if (viewsActivationMap.containsKey(view))
			throw new IllegalArgumentException("This view is already " +
					"registered for activation management!");
		ViewActivationData viewActivationData=new ViewActivationData();
		viewsActivationMap.put(view,viewActivationData);
		registerTouchListenerHierarchy(view,activationViewListener);
		if (view.isEnabled()) handleActiveView(view,viewActivationData); 
		//else view.setOnTouchListener(activationViewListener);
	}
	
	public void unregisterView(View view)
	{
		if (view==null)
			throw new NullPointerException("The view to unregister from " +
					"activation management must be non-null!");
		ViewActivationData viewActivationData=viewsActivationMap.get(view);
		if (viewActivationData==null)
			throw new IllegalArgumentException("This view is not registered " +
					"for activation management!");
		registerTouchListenerHierarchy(view,null);
		ObjectAnimator activationAnimator=viewActivationData.activationAnimator;
		if (activationAnimator!=null)
		{
			if (activationAnimator.isRunning())
			{
				view.setTag(R.id.remove_view_indication,true);
				activationAnimator.end();
			}
		}
		else
		{
			UntouchedViewTimeoutCallback untouchedCallback=viewActivationData.
					untouchedTimeoutCallback;
			if (untouchedCallback!=null) 
			{
				untouchedCallback.stop();
				Handler handler=new Handler(Looper.getMainLooper());
				handler.removeCallbacks(untouchedCallback); //Not working!!!
			}
			releaseActivationResources(viewActivationData);
			viewsActivationMap.remove(view);
		}
	}
	
	private void registerTouchListenerHierarchy(View view,View.OnTouchListener 
			listener)
	{
		//View.OnTouchListener listener=(responsive?activationViewListener:null);
		view.setOnTouchListener(listener);
		if (view instanceof ViewGroup)
		{
			ViewGroup viewGroup=(ViewGroup)view;
			int numChildren=viewGroup.getChildCount();
			for (int counter=0;counter<numChildren;counter++)
			{
				View childView=viewGroup.getChildAt(counter);
				/*childView.setClickable(responsive);
				childView.setLongClickable(responsive); //Needed?*/
				//childView.setEnabled(responsive);
				//childView.setFocusable(responsive);
				childView.setOnTouchListener(listener);
				//if (childView instanceof ViewGroup)
				registerTouchListenerHierarchy(childView,listener);
			}
		}
	}
	
	private UntouchedViewTimeoutCallback getUntouchedTimeoutCallback()
	{
		if (!untouchedCallbacksPool.isEmpty()) 
		{
			UntouchedViewTimeoutCallback untouchedCallback=untouchedCallbacksPool.
					remove(0);
			untouchedCallback.reset();
			return untouchedCallback;
		}
		else return new UntouchedViewTimeoutCallback();
	}
	
	private ObjectAnimator getActivationAnimator()
	{
		if (!activationAnimatorsPool.isEmpty())
			return activationAnimatorsPool.remove(0);
		else return activationAnimatorPrototype.clone();
	}
	
	private void handleActiveView(View view,ViewActivationData viewActivationData)
	{
		UntouchedViewTimeoutCallback untouchedCallback=getUntouchedTimeoutCallback();
		Log.i("MapView","Posting: " + untouchedCallback);
		untouchedCallback.setUntouchedView(view);
		viewActivationData.untouchedTimeoutCallback=untouchedCallback;
		Handler handler=new Handler(Looper.getMainLooper());
		handler.postDelayed(untouchedCallback,UNTOUCHED_TIMEOUT);
	}
	
	private void activateView(View view,ViewActivationData viewActivationData)
	{
		Log.i("MapView","Activating");
		ObjectAnimator activationAnimator=getActivationAnimator();
		viewActivationData.activationAnimator=activationAnimator;
		activationAnimator.setTarget(view);
		activationAnimator.setFloatValues(MAX_OPACITY_VALUE);
		activationAnimator.start();
	}
	
	private void releaseActivationResources(ViewActivationData viewActivationData)
	{
		UntouchedViewTimeoutCallback untouchedCallback=viewActivationData.
				untouchedTimeoutCallback;
		if (untouchedCallback!=null)
		{
			if (untouchedCallback.getState()==CallbackState.FINISHED)
				untouchedCallbacksPool.add(untouchedCallback);
			viewActivationData.untouchedTimeoutCallback=null;
		}
		if (viewActivationData.activationAnimator!=null)
		{
			activationAnimatorsPool.add(viewActivationData.activationAnimator);
			viewActivationData.activationAnimator=null;
		}
	}
}
