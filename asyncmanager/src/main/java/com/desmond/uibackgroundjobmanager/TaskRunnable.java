package com.desmond.uibackgroundjobmanager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Task that runs its long operation in a background thread, and posts its result
 * to be executed on the main UI thread.
 *
 * @param <Result> Expected result type from the #doLongOperation()
 * @param <ResultHandler> Handler that might be required to handle the result on the UI thread
 */
public abstract class TaskRunnable<Result, ResultHandler> implements Runnable {

    private static final String TAG = TaskRunnable.class.getSimpleName();

    private BackgroundTask mTask;
    private AsyncStatus mStatus;
    private WeakReference<ResultHandler> mResultHandler;
    private Result mResult;

    boolean mShouldPersist = false;

    interface TaskRunnableMethods {
        void setCurrentThread(Thread currentThread);
        Thread getCurrentThread();
        TaskRunnable getTaskRunnable();
        void completedJob();
    }

    public TaskRunnable() {
        mStatus = new AsyncStatus();
        mResultHandler = null;
    }

    public TaskRunnable(@NonNull ResultHandler resultHandler) {
        mStatus = new AsyncStatus();
        mResultHandler = new WeakReference<>(resultHandler);
    }

    @Override
    public void run() {
        final long threadId = Thread.currentThread().getId();
        try {
            checkForThreadInterruption();
            mTask.setCurrentThread(Thread.currentThread());

            if (mStatus.isWaiting()) {
                mStatus.started();
                Log.d(TAG, threadId + " thread is doing long operation");
                mResult = doLongOperation();

                checkForThreadInterruption();
                UIThreadUtility.post(this);
            } else if (mStatus.isStarted()) {
                callback();
                mStatus.completed();
            }
        } catch (InterruptedException e) {
            mStatus.cancelled();
            Log.d(TAG, threadId + " thread is interrupted");
        } finally {
            Log.d(TAG, threadId + " cleaning up");
            cleanUp();
            // Clears the Thread's interrupt flag
            Thread.interrupted();
        }
    }

    public abstract Result doLongOperation();

    public void callback(Result result) {}
    public void callback(ResultHandler handler, Result result) {}

    private void checkForThreadInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    private void callback() {
        synchronized (this) {
            if (isActive()) {
                if (mResultHandler == null) {
                    callback(mResult);
                } else {
                    callback(mResultHandler.get(), mResult);
                }
            }
        }
    }

    private boolean isActive() {
        if (mTask == null)          return false;
        if (mResultHandler == null) return true;

        ResultHandler handler = mResultHandler.get();
        return handler != null;
    }

    private void cleanUp() {
        synchronized (this) {
            if (mTask != null && mStatus.isCleanable()) {
                mTask.completedJob();
                mStatus.isWaiting();
                mResult = null;
                mResultHandler = null;
                mTask = null;
            }
        }
    }

    void setTask(BackgroundTask task) {
        mTask = task;
    }

    BackgroundTask getTask() {
        return mTask;
    }
}
