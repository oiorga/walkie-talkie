package walkie.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 *
 *
 *
 */
class LifeCycleObserver (
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val callback: () -> Unit = { noop() }
): DefaultLifecycleObserver {
    private val tag = "LifeCycleObserver(${this.toString()})"

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        noop()
        Log.d(tag, "${this.toString()} onCreate")
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        noop()
        Log.d(tag, "${this.toString()} onStart")
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        noop()
        Log.d(tag, "${this.toString()} onPause")
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        noop()
        Log.d(tag, "${this.toString()} onResume")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        noop()
        Log.d(tag, "${this.toString()} onStop")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        noop()
        Log.d(tag, "${this.toString()} onDestroy")
    }

    fun enable() {
        noop()
    }

    fun disable() {
        noop()
    }
}

/**
 *
 *
 *
 */
class LifeCycleLogsClass(private val activity: Activity? = null): Application.ActivityLifecycleCallbacks {
    private val tag= "WalkieLifeCycleLogs ${this.toString()}"

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityCreated")
    }

    override fun onActivityStarted(p0: Activity) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityStarted")
    }

    override fun onActivityResumed(p0: Activity) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityResumed")
    }

    override fun onActivityPaused(p0: Activity) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityPaused")
    }

    override fun onActivityStopped(p0: Activity) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityStopped")
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivitySaveInstanceState")
    }

    override fun onActivityDestroyed(p0: Activity) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityDestroyed")
    }

    override fun onActivityPostCreated(p0: Activity, p1: Bundle?) {
        if ((null != activity) && (p0 != activity)) return
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityPostCreated")
    }
}

/**
 *
 *
 *
 */
object LifeCycleLogs: Application.ActivityLifecycleCallbacks {
    private val tag= "WalkieLifeCycleLogs(${this.toString()})"

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityCreated")
    }

    override fun onActivityStarted(p0: Activity) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityStarted")
    }

    override fun onActivityResumed(p0: Activity) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityResumed")
    }

    override fun onActivityPaused(p0: Activity) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityPaused")
    }

    override fun onActivityStopped(p0: Activity) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityStopped")
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivitySaveInstanceState")
    }

    override fun onActivityDestroyed(p0: Activity) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityDestroyed")
    }

    override fun onActivityPostCreated(p0: Activity, p1: Bundle?) {
        Log.d(tag, "${this.toString()} ${p0.componentName}: onActivityPostCreated")
    }
}
