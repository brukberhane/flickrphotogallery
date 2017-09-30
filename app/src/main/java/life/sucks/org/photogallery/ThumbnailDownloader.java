package life.sucks.org.photogallery;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.lang.annotation.Target;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD =  0;

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener){
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler){
        super(TAG);
        mResponseHandler = responseHandler;
        //TODO: checking cache
        mCache = new LruCache<String, Bitmap>(CACHE_SIZE);
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onLooperPrepared(){
        mRequestHandler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                if (msg.what == MESSAGE_DOWNLOAD){

                    @SuppressWarnings("unchecked")
                    T target = (T)msg.obj;

                    Log.i(TAG, "Got a request URL: "+mRequestMap.get(target));
                    handleRequest(target);
                } else if (msg.what == MESSAGE_PRELOAD){
                    String url = (String)msg.obj;
                    preload(url);
                }
            }
        };
    }

    @Override
    public boolean quit(){
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url){
        Log.i(TAG, "Got a URL: "+url);

        if (url == null){
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void clearQueue(){
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    private void handleRequest(final T target){
        try{
            final String url = mRequestMap.get(target);

            if (url == null){
                return;
            }

            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            final Bitmap bitmap = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "Bitmap created!");

            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mRequestMap.get(target) != url || mHasQuit){
                        return;
                    }

                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe){
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

    //TODO: Implement caching. It will maybe work? We'll find out...
    private static final int MESSAGE_PRELOAD = 1;
    private static final int CACHE_SIZE = 400;

    LruCache<String, Bitmap> mCache;

    public void queuePreload(String url){
        if (mCache.get(url) != null)return;

        mRequestHandler
                .obtainMessage(MESSAGE_PRELOAD, url)
                .sendToTarget();
    }

    public Bitmap checkCache(String url){
        return mCache.get(url);
    }

    private Bitmap getBitmap(String url){
        try{
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            Bitmap bitmapDecode = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "Bitmap created");
            return bitmapDecode;
        } catch (IOException ioe){
            Log.e(TAG, "Error downloading image", ioe);
        }
        return null;
    }

    private void preload(final T target){
        String url = mRequestMap.get(target);
        preload(url);
    }

    private void preload(String url){
        if (url == null){
            return;
        }
        if (mCache.get(url) != null){
            Bitmap bitmap = getBitmap(url);
            if (bitmap != null){
                mCache.put(url, bitmap);
            }
        }
    }

}
