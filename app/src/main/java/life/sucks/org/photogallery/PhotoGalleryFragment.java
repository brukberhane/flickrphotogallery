package life.sucks.org.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";
    private static final int COL_WIDTH = 300;

    private int lastFetchedPage = 1;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    //TODO:  test var:
    private int mPage = 0;
    private boolean loading = false;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemsTask().execute();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
                        Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
                        target.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    private void setupAdapter(){
        if (isAdded()){
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.fragment_photo_gallery_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                PhotoAdapter adapter = (PhotoAdapter)recyclerView.getAdapter();//Must be cast to PhotoAdapter because non-inherited method is used.
                int lastPosition = adapter.getLastBoundPosition();
                GridLayoutManager layoutManager = (GridLayoutManager)recyclerView.getLayoutManager();
                int loadBufferPosition = 1;

                if (lastPosition >= adapter.getItemCount() - layoutManager.getSpanCount() - loadBufferPosition){
                    new FetchItemsTask().execute(lastPosition + 1);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int numColumns = mPhotoRecyclerView.getWidth() / COL_WIDTH;
                GridLayoutManager layoutManager = (GridLayoutManager)mPhotoRecyclerView.getLayoutManager();
                layoutManager.setSpanCount(numColumns);
            }
        });

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView  mItemImageView;

        public PhotoHolder(View itemView){
            super(itemView);

            mItemImageView = (ImageView)itemView
                    .findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindDrawable(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);
        }

    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>{

        private List<GalleryItem> mGalleryItems;
        private int lastBoundPosition;

        public int getLastBoundPosition(){
            return lastBoundPosition;
        }

        public PhotoAdapter(List<GalleryItem> galleryItems){
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType){
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position){
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeHolder = getResources().getDrawable(R.drawable.bill_up_close);
            photoHolder.bindDrawable(placeHolder);
            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
            lastBoundPosition = position;
            Log.i(TAG, "Last bound position is "+Integer.toString(lastBoundPosition));
        }

        @Override
        public int getItemCount(){
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>>{
        @Override
        protected List<GalleryItem> doInBackground(Integer... params){
            return new FlickrFetchr().fetchItems(lastFetchedPage);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items){
//            mItems = items;
//            setupAdapter();
            if (lastFetchedPage > 1){
                mItems.addAll(items);
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            } else {
                mItems = items;
                setupAdapter();
            }
            lastFetchedPage++;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    //TODO: Second try at implementing caching
//    private void loadData(){
//        if (loading)return;
//        loading = true;
//        mPage++;
//        new FetchItemsTask().execute();
//    }
//
//    private class GalleryItemAdapter extends ArrayAdapter<GalleryItem>{
//        public GalleryItemAdapter(ArrayList<GalleryItem> items){
//            super(getActivity(), 0, items);
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent){
//            if (convertView == null){
//                convertView = getActivity().getLayoutInflater()
//                        .inflate(R.layout.gallery_item, parent, false);
//            }
//            ImageView imageView = (ImageView)convertView.findViewById(
//                    R.id.fragment_photo_gallery_image_view
//                    );
//        }TODO: this was a fail too...
//    }


    //TODO: This is a test implementation of caching. it prally won't work
    /*private class GalleryItemAdapter extends ArrayAdapter<GalleryItem>{

        public GalleryItemAdapter(ArrayList<GalleryItem> items){
            super(getActivity(), 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            if (convertView == null){
                convertView = getActivity().getLayoutInflater().inflate(
                        R.layout.gallery_item, parent, false
                );
            }

            ImageView imageView = (ImageView) convertView
                    .findViewById(R.id.fragment_photo_gallery_image_view);
            imageView.setImageResource(R.drawable.bill_up_close);
            GalleryItem item = getItem(position);

            Bitmap bitmap =
                    SingletonLruCache.getBitmapFromMemoryCache(item.getUrl());

            if (bitmap == null){

            } else {
                if (isVisible()){
                    imageView.setImageBitmap(bitmap);
                }
            }

            for (int i = position-10; i <=position+10; i++){
                if (i >= 0 && i < mItems.size()){
                    String url = mItems.get(i).getUrl();
                    if (SingletonLruCache.getBitmapFromMemoryCache(url) == null){

                    }
                }
            }
        }

    }*/
    //Well that was a fail...

}
