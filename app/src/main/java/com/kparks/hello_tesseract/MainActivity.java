package com.kparks.hello_tesseract;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String PREFIX = "HELLOTESS";
    public static final String EXTENSION = ".PNG";
    protected Uri mMediaUri;
    protected String mFullMediaPath;
    protected Button mCaptureButton;
    protected Button mGalleryButton;
    protected ImageView mThumbnail;
    protected ProgressBar mProgressBar;
    protected Pix mPix;
    protected int mOrientation;
    protected int mRequestType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mCaptureButton = (Button) findViewById(R.id.button_captureImage);
        mGalleryButton = (Button) findViewById(R.id.button_galleryImage);
        mThumbnail = (ImageView) findViewById(R.id.imageView_thumbnail);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mPix = null;
        mOrientation = -1;
        mRequestType = -1;

        if(savedInstanceState == null) {
            mProgressBar.setVisibility(View.INVISIBLE);
        }

        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Take a photo using system camera
                Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                //Find a location to store the image
                mMediaUri = getOutputMediaFileUri(Constants.MEDIA_TYPE_IMAGE);
                if(mMediaUri == null) {
                    //display an error
                    Toast.makeText(MainActivity.this, "Cannot save images", Toast.LENGTH_LONG).show();
                }
                else {
                    //Pass along the URI to use for saving
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
                    //Start camera intent
                    startActivityForResult(takePhotoIntent, Constants.TAKE_PHOTO_REQUEST);
                }
            }
        });

        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Choose a photo from the gallery
                Intent galleryPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
                galleryPhotoIntent.setType("image/*");
                startActivityForResult(galleryPhotoIntent, Constants.PICK_PHOTO_REQUEST);
            }
        });

        //TODO: anything actually to do with TessBaseAPI, this serves as a hint it's there
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            mRequestType = requestCode;
            if (mRequestType == Constants.PICK_PHOTO_REQUEST) {
                if (intent == null) {
                    Toast.makeText(this, "An error has occurred. Try again.", Toast.LENGTH_LONG).show();
                    return;
                } else {
                    mMediaUri = intent.getData();
                }
            } else {
                //Let system know we added something
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(mMediaUri);
                sendBroadcast(mediaScanIntent);
            }
            //Get the bitmap data first & downsample it
            Log.d(TAG, "MaxH: " + mThumbnail.getMaxHeight());
            Log.d(TAG, "MaxW: " + mThumbnail.getMaxWidth());
            loadBitmap(mMediaUri, mThumbnail, mRequestType, this);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void loadBitmap(Uri mediaUri, ImageView imageView, int requestType, Context context) {
        BitmapWorkerTask task = new BitmapWorkerTask(imageView, mediaUri, requestType, context);
        task.execute(mediaUri, requestType, mThumbnail.getMaxWidth(), mThumbnail.getMaxHeight());
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Uri getOutputMediaFileUri(int mediaType) {
        //To be safe checkk SDCard (external) storage
        //using Environment.getExternalStorageState() before doing this
        if(isExternalStorageAvailable()) {
            //get the Uri
            //get the external storage directory
            String appName = MainActivity.this.getString(R.string.app_name);
            File mediaStorageDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    appName);
            //create our own sub dir
            Log.d(TAG, "Path to dir: " + mediaStorageDir.toString());
            if(! mediaStorageDir.exists()) {
                if(!mediaStorageDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory");
                    return null;
                }
            }
            //create filename
            //create file
            File mediaFile;
            Date now = new Date();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now);
            String path = mediaStorageDir.getPath() + File.separator;
            mediaFile = new File(path + "IMG_" + timestamp + ".jpg");

            Log.d(TAG, "File: " + Uri.fromFile(mediaFile));
            //return the file's uri
            return Uri.fromFile(mediaFile);
        }
        else {
            return null;
        }
    }

    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        if(state.equals(Environment.MEDIA_MOUNTED)) {
            return true;
        }
        else {
            return false;
        }
    }

//    @Override
//    public void onSaveInstanceState(Bundle savedInstanceState) {
//        if(mPix != null) {
//            File outputDir = this.getCacheDir();
//            try {
//                File tempFile = File.createTempFile(PREFIX, EXTENSION, outputDir);
//                FileOutputStream stream = new FileOutputStream(tempFile);
//                if(mMediaUri != null && mRequestType != -1) {
//                    if (mOrientation == -1) {
//                        mOrientation = getImageOrientation(mRequestType, mMediaUri);
//                        mPix = Rotate.rotate(mPix, mOrientation);
//                    }
//                    Bitmap bmp = WriteFile.writeBitmap(mPix);
//                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
//                    stream.close();
//                    bmp.recycle();
//                    savedInstanceState.putString("image", tempFile.getAbsolutePath());
//                }
//            } catch (IOException e) {
//                Log.e(TAG, "Couldn't save temp file");
//                e.printStackTrace();
//            }
//        }
//        super.onSaveInstanceState(savedInstanceState);
//    }
//
//    @Override
//    public void onRestoreInstanceState(Bundle savedInstanceState) {
//        super.onRestoreInstanceState(savedInstanceState);
//
//        String path = savedInstanceState.getString("image");
//        if(!path.isEmpty()) {
//            FileInputStream stream = null;
//            try {
//                stream = this.openFileInput(path);
//                Bitmap bmp = BitmapFactory.decodeStream(stream);
//                stream.close();
//                if(bmp != null && mThumbnail != null) {
//                    mThumbnail.setImageBitmap(bmp);
//                    mThumbnail.invalidate();
//                }
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        } else {
//            Log.e(TAG, "Path is empty");
//        }
//    }


}
