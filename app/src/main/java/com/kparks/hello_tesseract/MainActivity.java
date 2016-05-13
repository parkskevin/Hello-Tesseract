package com.kparks.hello_tesseract;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
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
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int TAKE_PHOTO_REQUEST = 0;
    public static final int PICK_PHOTO_REQUEST = 1;
    public static final int MEDIA_TYPE_IMAGE = 1;
    protected Uri mMediaUri;
    protected Button mCaptureButton;
    protected Button mGalleryButton;
    protected ImageView mThumbnail;
    protected int mOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mCaptureButton = (Button) findViewById(R.id.button_captureImage);
        mGalleryButton = (Button) findViewById(R.id.button_galleryImage);
        mThumbnail = (ImageView) findViewById(R.id.imageView_thumbnail);
        //TODO: anything actually to do with TessBaseAPI, this serves as a hint it's there
        TessBaseAPI tessBaseAPI = new TessBaseAPI();

        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Take a photo using system camera
                Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                //Find a location to store the image
                mMediaUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
                if(mMediaUri == null) {
                    //display an error
                    Toast.makeText(MainActivity.this, "Cannot save images", Toast.LENGTH_LONG).show();
                }
                else {
                    //Pass along the URI to use for saving
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
                    //Start camera intent
                    startActivityForResult(takePhotoIntent, TAKE_PHOTO_REQUEST);
                }
            }
        });

        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Choose a photo from the gallery
                Intent galleryPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
                galleryPhotoIntent.setType("image/*");
                startActivityForResult(galleryPhotoIntent, PICK_PHOTO_REQUEST);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_PHOTO_REQUEST) {
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
            //Get the proper orientation of the image
            try {
                mOrientation = getImageOrientation(PICK_PHOTO_REQUEST, mMediaUri);
            } catch (IOException e) {
                Log.e(TAG, "Get orientation error!");
                e.printStackTrace();
            }

            //Show the image using leptonica
            Pix mPix = null;
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mMediaUri);
                mPix = ReadFile.readBitmap(bitmap);
                if (mPix == null) {
                    Toast.makeText(this, "Error: mPix is null.", Toast.LENGTH_LONG).show();
                    return;
                } else {
                    mPix = Rotate.rotate(mPix, mOrientation);
                    mThumbnail.setImageBitmap(WriteFile.writeBitmap(mPix));
                    mThumbnail.invalidate();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int getImageOrientation(int requestType, Uri mediaUri) throws IOException {
        int orientation = -1;
        Cursor cursor = null;
        if(requestType == PICK_PHOTO_REQUEST) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                String wholeID = null;
                wholeID = DocumentsContract.getDocumentId(mediaUri);
                String id = wholeID.split(":")[1];
                String column[] = {MediaStore.Images.Media.ORIENTATION};
                String sel = MediaStore.Images.Media._ID + "=?";
                cursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        column, sel, new String[]{id}, null);
                if (cursor != null && cursor.moveToFirst()) {
                    orientation = cursor.getInt(cursor.getColumnIndex(column[0]));
                }
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            ExifInterface exifInterface = new ExifInterface(mediaUri.getPath());
            orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            switch(orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    orientation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    orientation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    orientation = 270;
                    break;
                default:
                    orientation = 0;
                    break;
            }
        }
        Log.d(TAG, "Orientation: " + mOrientation);
        return orientation;
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
}
