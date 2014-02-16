package it.feio.android.omninotes.async;

import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.utils.BitmapHelper;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.StorageManager;
import it.feio.android.omninotes.utils.date.DateHelper;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;
import android.widget.ImageView;

public class ThumbnailLoaderTask extends
		AsyncTask<Attachment, Void, Bitmap> {

	private final int FADE_IN_TIME = 190;
	
	private final Activity mActivity;
	private final WeakReference<ImageView> imageViewReference;
	private int width = 100;
	private int height = 100;

	public ThumbnailLoaderTask(Activity activity, ImageView imageView,
			int thumbnailSize) {
		this.mActivity = activity;
		imageViewReference = new WeakReference<ImageView>(imageView);
		width = thumbnailSize;
		height = thumbnailSize;
	}

	@Override
	protected Bitmap doInBackground(Attachment... params) {
		Bitmap bmp = null;
		Attachment mAttachment = params[0];

		String path = mAttachment.getUri().getPath();		
		// Creating a key based on path and thumbnail size to re-use the same
		// AsyncTask both for list and detail
		String cacheKey = path + width;

		// Requesting from Application an instance of the cache
		OmniNotes app = ((OmniNotes) mActivity.getApplication());

		// Video
		if (Constants.MIME_TYPE_VIDEO.equals(mAttachment.getMime_type())) {
			// Tries to retrieve full path from ContentResolver if is a new
			// video
			path = StorageManager.getRealPathFromURI(mActivity,
					mAttachment.getUri());
			// .. or directly from local directory otherwise
			if (path == null) {
				path = mAttachment.getUri().getPath();
			}

			// Fetch from cache if possible
			bmp = app.getBitmapFromCache(cacheKey);
			// Otherwise creates thumbnail
			if (bmp == null) {
				bmp = ThumbnailUtils.createVideoThumbnail(path,
						Thumbnails.MINI_KIND);
				bmp = createVideoThumbnail(bmp);
				app.addBitmapToCache(cacheKey, bmp);
			}

			// Image
		} else if (Constants.MIME_TYPE_IMAGE.equals(mAttachment.getMime_type())) {
			try {
				// Fetch from cache if possible
				bmp = app.getBitmapFromCache(cacheKey);
				// Otherwise creates thumbnail
				if (bmp == null) {
					try {
						bmp = checkIfBroken(BitmapHelper.decodeSampledFromUri(
								mActivity, mAttachment.getUri(), width, height));
//						cache.addBitmap(path, bmp);
						app.addBitmapToCache(cacheKey, bmp);
					} catch (FileNotFoundException e) {
						Log.e(Constants.TAG,
								"Error getting bitmap for thumbnail " + path);
					}
				}
			} catch (NullPointerException e) {
				bmp = checkIfBroken(null);
//				cache.addBitmap(cacheKey, bmp);
				app.addBitmapToCache(cacheKey, bmp);
			}

			// Audio
		} else if (Constants.MIME_TYPE_AUDIO.equals(mAttachment.getMime_type())) {
			// Fetch from cache if possible
			bmp = app.getBitmapFromCache(cacheKey);
			// Otherwise creates thumbnail
			if (bmp == null) {
				String text = "";
				try {
					text = DateHelper.getLocalizedDateTime(
							mActivity,
							mAttachment.getUri().getLastPathSegment()
									.split("\\.")[0],
							Constants.DATE_FORMAT_SORTABLE);
				} catch (NullPointerException e) {
					text = DateHelper.getLocalizedDateTime(
							mActivity,
							mAttachment.getUri().getLastPathSegment()
									.split("\\.")[0], "yyyyMMddHHmmss");
				}
				bmp = ThumbnailUtils.extractThumbnail(BitmapFactory
						.decodeResource(mActivity.getResources(),
								R.drawable.play), width, height);
				bmp = BitmapHelper.drawTextToBitmap(mActivity, bmp, text, null,
						-10, 3.3f,
						mActivity.getResources().getColor(R.color.text_gray));
				app.addBitmapToCache(cacheKey, bmp);
			}
		}

		return bmp;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
//		if (imageViewReference != null && bitmap != null) {
//			final ImageView imageView = imageViewReference.get();
//			if (imageView != null) {
//				imageView.setImageBitmap(bitmap);
//			}
//		}
		fadeInImage(bitmap);
	}

	/**
	 * Draws a watermark on ImageView to highlight videos
	 * 
	 * @param bmp
	 * @param overlay
	 * @return
	 */
	public Bitmap createVideoThumbnail(Bitmap video) {
		Bitmap mark = ThumbnailUtils.extractThumbnail(
				BitmapFactory.decodeResource(mActivity.getResources(),
						R.drawable.play_white), width, height);
		Bitmap thumbnail = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(thumbnail);
		Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

		canvas.drawBitmap(checkIfBroken(video), 0, 0, null);
		canvas.drawBitmap(mark, 0, 0, null);

		return thumbnail;
	}

	private Bitmap checkIfBroken(Bitmap bmp) {

		// In case no thumbnail can be extracted from video
		if (bmp == null) {
			bmp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeResource(
					mActivity.getResources(), R.drawable.attachment_broken),
					width, height);
		}
		return bmp;
	}
	
	
	private void fadeInImage(Bitmap bitmap) {

		if (imageViewReference != null && bitmap != null) {
			final ImageView imageView = imageViewReference.get();
			if (imageView != null) {
				imageView.setImageBitmap(bitmap);

				// Transition drawable with a transparent drwabale and the final
				// bitmap
				final TransitionDrawable td = new TransitionDrawable(new Drawable[] { new ColorDrawable(Color.TRANSPARENT),
						new BitmapDrawable(mActivity.getResources(), bitmap) });
				// Set background to loading bitmap
				// imageView.setBackgroundDrawable(
				// new BitmapDrawable(mLoadingBitmap));

				if (td != null) {
					imageView.setImageDrawable(td);
					td.startTransition(FADE_IN_TIME);
				} 
			}
		}
	}

}